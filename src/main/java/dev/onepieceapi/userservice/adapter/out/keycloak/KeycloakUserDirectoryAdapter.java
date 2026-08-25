package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakAdminProperties;
import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakInvitationProperties;
import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import dev.onepieceapi.userservice.application.exception.EmailDeliveryFailedException;
import dev.onepieceapi.userservice.application.exception.InvitationNotResendableException;
import dev.onepieceapi.userservice.application.exception.UserNotFoundException;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Keycloak-backed implementation of {@link UserDirectoryPort} - the only place in this
 * codebase that talks to the Keycloak Admin API. Swapping identity providers means
 * writing a new adapter against this same port, not touching
 * {@code AdminUserQueryService} or the domain.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
@Slf4j
public class KeycloakUserDirectoryAdapter implements UserDirectoryPort {

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

	private final Keycloak keycloakAdminClient;

	private final ExecutorService keycloakAdminExecutor;

	private final KeycloakAdminProperties keycloakAdminProperties;

	private final KeycloakInvitationProperties keycloakInvitationProperties;

	private final Clock clock;

	@Override
	public List<User> findUsers(int offset, int limit) {
		try {
			UsersResource users = getRealm().users();

			List<CompletableFuture<User>> futures = users.list(offset, limit)
				.stream()
				.map(user -> fetchAsync(users, user))
				.toList();

			return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
				.thenApply(_ -> futures.stream().map(CompletableFuture::join).toList())
				.join();
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to list users from Keycloak", ex);
		}
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
	public long countUsers() {
		try {
			return getRealm().users().count();
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to count users in Keycloak", ex);
		}
	}

	@Override
	public User inviteUser(String email, Set<RealmRole> roles) {
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
		return new User(userId, email, email, AccountStatus.PENDING, roleNames(roles), Instant.now(this.clock));
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

	private static List<String> roleNames(Set<RealmRole> roles) {
		return roles.stream().map(RealmRole::name).toList();
	}

	@Override
	public void rollbackInvitation(UUID userId) {
		try {
			getRealm().users().get(userId.toString()).remove();
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to remove Keycloak user " + userId, ex);
		}
	}

	@Override
	public User resendInvitation(UUID userId) {
		UsersResource users = getRealm().users();
		User user;
		try {
			user = toUser(users, requireRepresentation(users, userId));
		}
		catch (UserNotFoundException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to load Keycloak user " + userId, ex);
		}

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
			rollbackInvitation(UUID.fromString(keycloakId));
		}
		catch (RuntimeException cleanupFailure) {
			String message = "Failed to roll back Keycloak user {} - manual cleanup needed";
			log.error(message, keycloakId, cleanupFailure);
			return;
		}
		log.warn("Rolled back Keycloak user {} after invitation failure", keycloakId, cause);
	}

	private void assignRealmRoles(UsersResource users, String keycloakId, Set<RealmRole> roles) {
		RolesResource realmRoles = getRealm().roles();
		List<RoleRepresentation> representations = roles.stream()
			.map(role -> realmRoles.get(role.name()).toRepresentation())
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
