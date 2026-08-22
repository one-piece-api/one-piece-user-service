package dev.onepieceapi.userservice.client.keycloak;

import dev.onepieceapi.userservice.config.keycloak.KeycloakAdminProperties;
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

@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
@Slf4j
public class KeycloakClient {

	private final Keycloak keycloakAdminClient;

	private final ExecutorService keycloakAdminExecutor;

	private final KeycloakAdminProperties keycloakAdminProperties;

	public List<UserRepresentation> users(int offset, int limit) {
		UsersResource users = getRealm().users();

		List<CompletableFuture<UserRepresentation>> futures = users.list(offset, limit)
			.stream()
			.map(user -> fetchRolesAsync(users, user))
			.toList();

		return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
			.thenApply(_ -> futures.stream().map(CompletableFuture::join).toList())
			.join();
	}

	private CompletableFuture<UserRepresentation> fetchRolesAsync(UsersResource usersResource,
			UserRepresentation user) {
		Supplier<UserRepresentation> withRoles = () -> injectRealmRoles(usersResource, user);
		return CompletableFuture.supplyAsync(withRoles, this.keycloakAdminExecutor);
	}

	private UserRepresentation injectRealmRoles(UsersResource usersResource, UserRepresentation user) {
		user.setRealmRoles(fetchRoles(usersResource, user.getId()));
		return user;
	}

	private List<String> fetchRoles(UsersResource usersResource, String userId) {
		return usersResource.get(userId)
			.roles()
			.realmLevel()
			.listAll()
			.stream()
			.map(RoleRepresentation::getName)
			.toList();
	}

	public long count() {
		return getRealm().users().count();
	}

	private RealmResource getRealm() {
		return this.keycloakAdminClient.realm(this.keycloakAdminProperties.realm());
	}

}
