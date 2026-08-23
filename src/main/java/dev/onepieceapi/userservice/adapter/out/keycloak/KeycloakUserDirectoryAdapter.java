package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakAdminProperties;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
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

	private final Keycloak keycloakAdminClient;

	private final ExecutorService keycloakAdminExecutor;

	private final KeycloakAdminProperties keycloakAdminProperties;

	@Override
	public List<UserAccount> findUsers(int offset, int limit) {
		UsersResource users = getRealm().users();

		List<CompletableFuture<UserAccount>> futures = users.list(offset, limit)
			.stream()
			.map(user -> fetchAsync(users, user))
			.toList();

		return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
			.thenApply(_ -> futures.stream().map(CompletableFuture::join).toList())
			.join();
	}

	private CompletableFuture<UserAccount> fetchAsync(UsersResource usersResource, UserRepresentation user) {
		Supplier<UserAccount> toUserAccount = () -> toUserAccount(usersResource, user);
		return CompletableFuture.supplyAsync(toUserAccount, this.keycloakAdminExecutor);
	}

	private UserAccount toUserAccount(UsersResource usersResource, UserRepresentation user) {
		List<String> realmRoles = fetchRealmRoles(usersResource, user.getId());
		return KeycloakUserAccountMapper.toUserAccount(user, realmRoles);
	}

	private List<String> fetchRealmRoles(UsersResource usersResource, String userId) {
		return usersResource.get(userId)
			.roles()
			.realmLevel()
			.listAll()
			.stream()
			.map(RoleRepresentation::getName)
			.toList();
	}

	@Override
	public long countUsers() {
		return getRealm().users().count();
	}

	private RealmResource getRealm() {
		return this.keycloakAdminClient.realm(this.keycloakAdminProperties.realm());
	}

}
