package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakAdminProperties;
import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakInvitationProperties;
import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
	 * Set on invite (UF-IDU-01) so the account has no usable credential until the invited
	 * user completes both - see {@link KeycloakUserMapper} for how account status is
	 * derived from these, and {@code KeycloakInvitationProperties} for where they are
	 * sent.
	 */
	private static final List<String> INVITATION_REQUIRED_ACTIONS = List.of("UPDATE_PASSWORD", "VERIFY_EMAIL");

	private final Keycloak keycloakAdminClient;

	private final ExecutorService keycloakAdminExecutor;

	private final KeycloakAdminProperties keycloakAdminProperties;

	private final KeycloakInvitationProperties keycloakInvitationProperties;

	@Override
	public List<User> findUsers(int offset, int limit) {
		UsersResource users = getRealm().users();

		List<CompletableFuture<User>> futures = users.list(offset, limit)
			.stream()
			.map(user -> fetchAsync(users, user))
			.toList();

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
		return KeycloakUserMapper.toUser(user, realmRoles);
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
		return getRealm().users().count();
	}

	@Override
	public User inviteUser(String email, Set<RealmRole> roles) {
		UsersResource users = getRealm().users();
		String keycloakId = createUnactivatedUser(users, email);

		try {
			assignRealmRoles(users, keycloakId, roles);
			triggerInvitationEmail(users, keycloakId);
		}
		catch (RuntimeException ex) {
			// Role assignment/email dispatch are separate Keycloak calls with no
			// transaction spanning the create above - without this, a failure here would
			// leave a half-provisioned account (created, but with no roles and/or no way
			// to activate it) that "email already registered" would then block from ever
			// being retried.
			deleteUser(keycloakId, ex);
			throw ex;
		}

		UUID userId = UUID.fromString(keycloakId);
		return new User(userId, email, AccountStatus.PENDING, roleNames(roles), Instant.now());
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
	}

	private static List<String> roleNames(Set<RealmRole> roles) {
		return roles.stream().map(RealmRole::name).toList();
	}

	@Override
	public void rollbackInvitation(UUID userId) {
		getRealm().users().get(userId.toString()).remove();
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
		users.get(keycloakId)
			.executeActionsEmail(this.keycloakInvitationProperties.redirectClientId(),
					this.keycloakInvitationProperties.redirectUri(), INVITATION_REQUIRED_ACTIONS);
	}

	private RealmResource getRealm() {
		return this.keycloakAdminClient.realm(this.keycloakAdminProperties.realm());
	}

}
