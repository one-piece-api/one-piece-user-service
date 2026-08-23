package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakAdminProperties;
import dev.onepieceapi.userservice.domain.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserDirectoryAdapterTest {

	private static final String LUFFY_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

	private static final String NAMI_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

	@Mock
	private Keycloak keycloakAdminClient;

	@Mock
	private RealmResource realmResource;

	@Mock
	private UsersResource usersResource;

	private KeycloakUserDirectoryAdapter keycloakUserDirectoryAdapter;

	@BeforeEach
	void setUp() {
		String clientId = "user-service-admin";
		var properties = new KeycloakAdminProperties("http://keycloak", "onepiece", clientId, "secret");
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		this.keycloakUserDirectoryAdapter = new KeycloakUserDirectoryAdapter(this.keycloakAdminClient, executor,
				properties);

		lenient().when(this.keycloakAdminClient.realm("onepiece")).thenReturn(this.realmResource);
		lenient().when(this.realmResource.users()).thenReturn(this.usersResource);
	}

	@Test
	void paginatesTheRealmsOwnUserListNatively() {
		when(this.usersResource.list(20, 10)).thenReturn(List.of());

		List<UserAccount> users = this.keycloakUserDirectoryAdapter.findUsers(20, 10);

		verify(this.usersResource).list(20, 10);
		assertThat(users).isEmpty();
	}

	@Test
	void injectsEachAccountsOwnRealmRolesConcurrently() {
		UserRepresentation luffy = userWithId(LUFFY_ID);
		UserRepresentation nami = userWithId(NAMI_ID);
		when(this.usersResource.list(0, 10)).thenReturn(List.of(luffy, nami));
		mockRoles(LUFFY_ID, "ADMIN");
		mockRoles(NAMI_ID, "EDITOR", "REVIEWER");

		List<UserAccount> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10);

		assertThat(users).extracting(UserAccount::userId, UserAccount::roles)
			.containsExactlyInAnyOrder(tuple(UUID.fromString(LUFFY_ID), List.of("ADMIN")),
					tuple(UUID.fromString(NAMI_ID), List.of("EDITOR", "REVIEWER")));
	}

	@Test
	void filtersOutTheAutoAssignedDefaultRealmRole() {
		UserRepresentation luffy = userWithId(LUFFY_ID);
		when(this.usersResource.list(0, 10)).thenReturn(List.of(luffy));
		mockRoles(LUFFY_ID, "ADMIN", "default-roles-onepiece");

		List<UserAccount> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10);

		assertThat(users.getFirst().roles()).containsExactly("ADMIN");
	}

	@Test
	void countsTheRealmsTotalUsers() {
		when(this.usersResource.count()).thenReturn(37);

		assertThat(this.keycloakUserDirectoryAdapter.countUsers()).isEqualTo(37L);
	}

	private void mockRoles(String keycloakId, String... roleNames) {
		var userResource = mock(UserResource.class);
		var roleMappingResource = mock(RoleMappingResource.class);
		var realmRoleScopeResource = mock(RoleScopeResource.class);
		when(this.usersResource.get(keycloakId)).thenReturn(userResource);
		when(userResource.roles()).thenReturn(roleMappingResource);
		when(roleMappingResource.realmLevel()).thenReturn(realmRoleScopeResource);
		List<RoleRepresentation> roles = List.of(roleNames)
			.stream()
			.map(name -> new RoleRepresentation(name, null, false))
			.toList();
		when(realmRoleScopeResource.listAll()).thenReturn(roles);
	}

	private static UserRepresentation userWithId(String keycloakId) {
		UserRepresentation user = new UserRepresentation();
		user.setId(keycloakId);
		user.setEmail(keycloakId + "@onepiece.local");
		user.setEnabled(true);
		return user;
	}

}
