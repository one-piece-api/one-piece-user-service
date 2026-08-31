package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakAdminProperties;
import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakInvitationProperties;
import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import dev.onepieceapi.userservice.application.exception.EmailDeliveryFailedException;
import dev.onepieceapi.userservice.application.exception.InvitationNotResendableException;
import dev.onepieceapi.userservice.application.exception.LastAdministratorException;
import dev.onepieceapi.userservice.application.exception.LastRoleException;
import dev.onepieceapi.userservice.application.exception.UserNotFoundException;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.User;
import dev.onepieceapi.userservice.domain.UserFilter;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Keycloak-backed implementation of {@link UserDirectoryPort} - user identities only; the
 * role/permission catalog itself is {@link KeycloakRoleDirectoryAdapter}'s concern
 * ({@code RoleDirectoryPort}). Swapping identity providers means writing a new adapter
 * against this same port, not touching {@code UserQueryService} or the domain.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
@Slf4j
public class KeycloakUserDirectoryAdapter implements UserDirectoryPort {

	/** UF-IDU-16's "last administrator" protection is tied to this specific role name. */
	private static final String ADMIN_ROLE = "ADMIN";

	/**
	 * Set on invite (UF-IDU-01) and re-sent as-is on resend (UF-IDU-03), so the invited
	 * user completes all three on Keycloak's own hosted pages before the account becomes
	 * usable: sets a password, chooses the {@code username} (UF-IDU-02 - Keycloak's
	 * native "Update Profile" screen, username editable by the account owner), and
	 * confirms the email address (UF-IDU-04). See {@link KeycloakUserMapper} for how
	 * account status is derived from these, and {@code KeycloakInvitationProperties} for
	 * where the browser is sent once all three are done. Keycloak itself decides the
	 * on-screen order, not this list.
	 */
	private static final List<String> INVITATION_REQUIRED_ACTIONS = List.of("UPDATE_PASSWORD", "UPDATE_PROFILE",
			"VERIFY_EMAIL");

	/**
	 * Keycloak's own admin-events log (realm setting {@code adminEventsEnabled}, see
	 * {@code onepiece-infrastructure}) already records every
	 * {@code execute-actions-email} call as an {@code ACTION} event with
	 * {@code resourcePath} "users/{userId}/execute-actions-email" - queried here
	 * (UF-IDU-03) to find when the current invitation was last (re)sent, rather than this
	 * application tracking that itself. See
	 * {@code docs/adr/0004-invitation-expiry-gating.md}.
	 */
	private static final List<String> ACTIONS = List.of("ACTION");

	private static final String EXECUTE_ACTIONS_EMAIL_PATH = "users/%s/execute-actions-email";

	/**
	 * Capped size for a filtered listing's candidate batch (Step 15) - generous for this
	 * project's realm scale, not a general solution for a large one; see
	 * {@code docs/adr/0008-users-list-filters.md}.
	 */
	private static final int FILTER_CANDIDATE_CAP = 500;

	private final Keycloak keycloakAdminClient;

	private final ExecutorService keycloakAdminExecutor;

	private final KeycloakAdminProperties keycloakAdminProperties;

	private final KeycloakInvitationProperties keycloakInvitationProperties;

	private final Clock clock;

	@Override
	public List<User> findUsers(int offset, int limit, UserFilter filter) {
		try {
			if (filter.isEmpty()) {
				UsersResource users = getRealm().users();
				return resolveUsers(users, users.list(offset, limit));
			}
			List<User> matches = loadFilterCandidates(filter);
			return matches.stream().skip(offset).limit(limit).toList();
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to list users from Keycloak", ex);
		}
	}

	@Override
	public long countUsers(UserFilter filter) {
		try {
			if (filter.isEmpty()) {
				return getRealm().users().count();
			}
			return loadFilterCandidates(filter).size();
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to count users in Keycloak", ex);
		}
	}

	/**
	 * Narrows via whichever single Keycloak call cuts the candidate set the most (role
	 * membership, then free-text search, else every user up to the cap), resolves each
	 * candidate the same way the unfiltered path does, then applies every filter field -
	 * including the one already used to narrow, which is harmless and keeps this method
	 * correct regardless of which pre-narrowing branch ran.
	 */
	private List<User> loadFilterCandidates(UserFilter filter) {
		UsersResource users = getRealm().users();
		List<UserRepresentation> candidates;
		if (filter.role() != null) {
			RolesResource realmRoles = getRealm().roles();
			candidates = realmRoles.get(filter.role()).getUserMembers(0, FILTER_CANDIDATE_CAP);
		}
		else if (filter.query() != null && !filter.query().isBlank()) {
			candidates = users.search(filter.query(), 0, FILTER_CANDIDATE_CAP);
		}
		else {
			candidates = users.list(0, FILTER_CANDIDATE_CAP);
		}
		return resolveUsers(users, candidates).stream().filter(user -> matches(user, filter)).toList();
	}

	private static boolean matches(User user, UserFilter filter) {
		String query = filter.query() == null ? null : filter.query().trim().toLowerCase(Locale.ROOT);
		boolean matchesQuery = query == null || query.isEmpty()
				|| user.username().toLowerCase(Locale.ROOT).contains(query)
				|| user.email().toLowerCase(Locale.ROOT).contains(query);
		boolean matchesRole = filter.role() == null || user.roles().contains(filter.role());
		boolean matchesStatus = filter.status() == null || user.status() == filter.status();
		return matchesQuery && matchesRole && matchesStatus;
	}

	private List<User> resolveUsers(UsersResource users, List<UserRepresentation> representations) {
		Stream<CompletableFuture<User>> pending = representations.stream().map(user -> fetchAsync(users, user));
		List<CompletableFuture<User>> futures = pending.toList();

		return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
			.thenApply(_ -> futures.stream().map(CompletableFuture::join).toList())
			.join();
	}

	private CompletableFuture<User> fetchAsync(UsersResource usersResource, UserRepresentation user) {
		Supplier<User> toUser = () -> toUser(usersResource, user);
		return CompletableFuture.supplyAsync(toUser, this.keycloakAdminExecutor);
	}

	private User toUser(UsersResource usersResource, UserRepresentation user) {
		List<String> realmRoles = fetchRealmRoles(usersResource, user.getId());
		User mapped = KeycloakUserMapper.toUser(user, realmRoles);
		return mapped.status() == AccountStatus.PENDING ? withExpiryResolved(mapped) : mapped;
	}

	private User withExpiryResolved(User pendingUser) {
		if (!isInvitationExpired(pendingUser.userId())) {
			return pendingUser;
		}
		return new User(pendingUser.userId(), pendingUser.username(), pendingUser.email(),
				AccountStatus.INVITATION_EXPIRED, pendingUser.roles(), pendingUser.createdAt());
	}

	private boolean isInvitationExpired(UUID userId) {
		Instant lastSentAt = lastInvitationSentAt(userId);
		if (lastSentAt == null) {
			// No recorded event at all (e.g. admin events were only just enabled, or the
			// account predates that) - nothing to compare against, so treat it as still
			// within its window rather than guessing it has expired.
			return false;
		}
		Duration lifespan = this.keycloakInvitationProperties.tokenLifespan();
		return lastSentAt.plus(lifespan).isBefore(Instant.now(this.clock));
	}

	private Instant lastInvitationSentAt(UUID userId) {
		String path = String.format(EXECUTE_ACTIONS_EMAIL_PATH, userId);
		List<AdminEventRepresentation> events = actionEvents(path);
		return events.isEmpty() ? null : Instant.ofEpochMilli(events.getFirst().getTime());
	}

	private List<AdminEventRepresentation> actionEvents(String path) {
		return getRealm().getAdminEvents(ACTIONS, null, null, null, null, path, null, null, null, 0, 1, "desc");
	}

	private List<String> fetchRealmRoles(UsersResource usersResource, String userId) {
		return usersResource.get(userId)
			.roles()
			.realmLevel()
			.listAll()
			.stream()
			.map(RoleRepresentation::getName)
			.filter(name -> !this.keycloakAdminProperties.excludedRealmRoles().contains(name))
			.toList();
	}

	@Override
	public User findUser(UUID userId) {
		return loadUser(getRealm().users(), userId);
	}

	@Override
	public User inviteUser(String email, Set<String> roles) {
		UsersResource users = getRealm().users();
		String keycloakId = createUnactivatedUser(users, email);

		// Role assignment and email dispatch are separate Keycloak calls with no
		// transaction spanning the create above - without rolling back on either
		// failure, a half-provisioned account (created, but with no roles and/or no way
		// to activate it) would block from ever being retried by "email already
		// registered". Role assignment failing is a genuine, unanticipated
		// Keycloak-communication problem; the email failing is not - see
		// EmailDeliveryFailedException.
		try {
			assignRealmRoles(users, keycloakId, roles);
		}
		catch (RuntimeException ex) {
			deleteUser(keycloakId, ex);
			throw new KeycloakCommunicationException("Failed to finish provisioning " + email, ex);
		}

		try {
			triggerInvitationEmail(users, keycloakId);
		}
		catch (RuntimeException ex) {
			log.warn("Could not send the invitation email to {}", email, ex);
			deleteUser(keycloakId, ex);
			throw new EmailDeliveryFailedException(email);
		}

		UUID userId = UUID.fromString(keycloakId);
		// The account's username is still the email placeholder set in
		// createUnactivatedUser -
		// the real, user-chosen one doesn't exist until activation (UF-IDU-02).
		Instant now = Instant.now(this.clock);
		return new User(userId, email, email, AccountStatus.PENDING, List.copyOf(roles), now);
	}

	private String createUnactivatedUser(UsersResource users, String email) {
		UserRepresentation newUser = new UserRepresentation();
		// Keycloak requires a username at creation, but per §2 of
		// application-user-identity-management.md the user-chosen username doesn't exist
		// until activation (UF-IDU-02, Step 5) - the email is used as a placeholder and
		// overwritten then. userId (Keycloak's own account id, the "sub" claim) is the
		// real, immutable identity link, never the username.
		newUser.setUsername(email);
		newUser.setEmail(email);
		newUser.setEmailVerified(false);
		newUser.setEnabled(true);
		newUser.setRequiredActions(INVITATION_REQUIRED_ACTIONS);

		try (Response response = users.create(newUser)) {
			if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
				throw new EmailAlreadyRegisteredException(email);
			}
			return CreatedResponseUtil.getCreatedId(response);
		}
		catch (EmailAlreadyRegisteredException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to create Keycloak account for " + email, ex);
		}
	}

	@Override
	public User resendInvitation(UUID userId) {
		UsersResource users = getRealm().users();
		User user = loadUser(users, userId);

		if (user.status() != AccountStatus.INVITATION_EXPIRED) {
			throw new InvitationNotResendableException(userId);
		}

		try {
			triggerInvitationEmail(users, userId.toString());
		}
		catch (RuntimeException ex) {
			log.warn("Could not resend the invitation email for {}", userId, ex);
			throw new EmailDeliveryFailedException(user.email());
		}
		// A fresh link was just issued - the account is PENDING again, not still expired,
		// so the caller (and the admin listing it feeds, once reloaded) reflects that
		// immediately rather than the pre-resend status.
		return new User(user.userId(), user.username(), user.email(), AccountStatus.PENDING, user.roles(),
				user.createdAt());
	}

	@Override
	public User assignRole(UUID userId, String role) {
		UsersResource users = getRealm().users();
		User user = loadUser(users, userId);
		if (user.roles().contains(role)) {
			return user;
		}

		try {
			assignRealmRoles(users, userId.toString(), Set.of(role));
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to assign role " + role + " to " + userId, ex);
		}

		List<String> updatedRoles = Stream.concat(user.roles().stream(), Stream.of(role)).toList();
		return withRoles(user, updatedRoles);
	}

	@Override
	public User revokeRole(UUID userId, String role) {
		UsersResource users = getRealm().users();
		User user = loadUser(users, userId);
		if (!user.roles().contains(role)) {
			return user;
		}
		// Checked in this order deliberately: when ADMIN is this account's only role (the
		// common case for a bootstrap admin), both rules technically apply - reporting
		// the
		// ADMIN-specific one first (UF-IDU-16) is more actionable than the generic
		// "at least one role must remain" (UF-IDU-15), since re-adding some other role
		// first wouldn't actually fix the real problem here.
		if (ADMIN_ROLE.equals(role) && !hasAnotherAdmin(userId)) {
			throw new LastAdministratorException(userId);
		}
		if (user.roles().size() == 1) {
			throw new LastRoleException(userId, role);
		}

		try {
			RoleRepresentation representation = getRealm().roles().get(role).toRepresentation();
			users.get(userId.toString()).roles().realmLevel().remove(List.of(representation));
		}
		catch (RuntimeException ex) {
			String message = "Failed to revoke role " + role + " from " + userId;
			throw new KeycloakCommunicationException(message, ex);
		}

		List<String> remainingRoles = user.roles().stream().filter(name -> !name.equals(role)).toList();
		return withRoles(user, remainingRoles);
	}

	private static User withRoles(User user, List<String> roles) {
		return new User(user.userId(), user.username(), user.email(), user.status(), roles, user.createdAt());
	}

	@Override
	public User revokeAccess(UUID userId) {
		UsersResource users = getRealm().users();
		User user = loadUser(users, userId);
		if (user.status() == AccountStatus.DISABLED) {
			return user;
		}
		if (user.roles().contains(ADMIN_ROLE) && !hasAnotherAdmin(userId)) {
			throw new LastAdministratorException(userId);
		}

		try {
			UserResource userResource = users.get(userId.toString());
			setEnabled(userResource, false);
			// Disabling alone leaves already-issued sessions/refresh tokens usable until
			// they naturally expire - UF-IDU-13 requires them invalidated immediately,
			// not
			// just blocked from renewing.
			userResource.logout();
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to revoke access for " + userId, ex);
		}

		return new User(user.userId(), user.username(), user.email(), AccountStatus.DISABLED, user.roles(),
				user.createdAt());
	}

	@Override
	public User reactivate(UUID userId) {
		UsersResource users = getRealm().users();
		User user = loadUser(users, userId);
		if (user.status() != AccountStatus.DISABLED) {
			return user;
		}

		try {
			setEnabled(users.get(userId.toString()), true);
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to reactivate " + userId, ex);
		}
		// Re-enabling doesn't by itself tell us whether the account was ACTIVE or still
		// mid-invitation (PENDING/INVITATION_EXPIRED) before it was disabled - reload
		// rather than assume ACTIVE, so KeycloakUserMapper/the expiry check re-derive the
		// correct status from the account's actual requiredActions.
		return loadUser(users, userId);
	}

	private void setEnabled(UserResource userResource, boolean enabled) {
		UserRepresentation representation = userResource.toRepresentation();
		representation.setEnabled(enabled);
		userResource.update(representation);
	}

	/**
	 * UF-IDU-16: fetches at most two ADMIN-role members (never the whole role membership,
	 * regardless of how many admins exist) and checks whether one of them is someone
	 * other than {@code excludedUserId} - the exact question a revoke needs answered,
	 * nothing more.
	 */
	private boolean hasAnotherAdmin(UUID excludedUserId) {
		List<UserRepresentation> admins = getRealm().roles().get(ADMIN_ROLE).getUserMembers(0, 2);
		return admins.stream().anyMatch(admin -> !admin.getId().equals(excludedUserId.toString()));
	}

	private User loadUser(UsersResource users, UUID userId) {
		try {
			return toUser(users, requireRepresentation(users, userId));
		}
		catch (UserNotFoundException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to load Keycloak user " + userId, ex);
		}
	}

	private UserRepresentation requireRepresentation(UsersResource users, UUID userId) {
		try {
			return users.get(userId.toString()).toRepresentation();
		}
		catch (NotFoundException ex) {
			throw new UserNotFoundException(userId);
		}
	}

	private void deleteUser(String keycloakId, RuntimeException cause) {
		try {
			getRealm().users().get(keycloakId).remove();
		}
		catch (RuntimeException cleanupFailure) {
			String message = "Failed to roll back Keycloak user {} - manual cleanup needed";
			log.error(message, keycloakId, cleanupFailure);
			return;
		}
		log.warn("Rolled back Keycloak user {} after invitation failure", keycloakId, cause);
	}

	private void assignRealmRoles(UsersResource users, String keycloakId, Set<String> roles) {
		RolesResource realmRoles = getRealm().roles();
		List<RoleRepresentation> representations = roles.stream()
			.map(role -> realmRoles.get(role).toRepresentation())
			.toList();
		users.get(keycloakId).roles().realmLevel().add(representations);
	}

	private void triggerInvitationEmail(UsersResource users, String keycloakId) {
		String clientId = this.keycloakInvitationProperties.redirectClientId();
		String redirectUri = this.keycloakInvitationProperties.redirectUri();
		int lifespanSeconds = (int) this.keycloakInvitationProperties.tokenLifespan().toSeconds();
		UserResource userResource = users.get(keycloakId);
		userResource.executeActionsEmail(clientId, redirectUri, lifespanSeconds, INVITATION_REQUIRED_ACTIONS);
	}

	private RealmResource getRealm() {
		return this.keycloakAdminClient.realm(this.keycloakAdminProperties.realm());
	}

}
