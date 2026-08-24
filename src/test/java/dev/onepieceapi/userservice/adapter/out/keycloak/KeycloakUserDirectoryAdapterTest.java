package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakAdminProperties;
import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakInvitationProperties;
import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserDirectoryAdapterTest {

	private static final String LUFFY_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

	private static final String NAMI_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

	private static final String INVITED_EMAIL = "usopp@onepiece.local";

	@Mock
	private Keycloak keycloakAdminClient;

	@Mock
	private RealmResource realmResource;

	@Mock
	private UsersResource usersResource;

	@Captor
	private ArgumentCaptor<UserRepresentation> newUserCaptor;

	private KeycloakUserDirectoryAdapter keycloakUserDirectoryAdapter;

	@BeforeEach
	void setUp() {
		String clientId = "user-service-admin";
		var adminProperties = new KeycloakAdminProperties("http://keycloak", "onepiece", clientId, "secret",
				Set.of("default-roles-onepiece"));
		var invitationProperties = new KeycloakInvitationProperties("onepiece-proxy", "http://localhost:4180/");
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		this.keycloakUserDirectoryAdapter = new KeycloakUserDirectoryAdapter(this.keycloakAdminClient, executor,
				adminProperties, invitationProperties);

		lenient().when(this.keycloakAdminClient.realm("onepiece")).thenReturn(this.realmResource);
		lenient().when(this.realmResource.users()).thenReturn(this.usersResource);
	}

	@Test
	void paginatesTheRealmsOwnUserListNatively() {
		when(this.usersResource.list(20, 10)).thenReturn(List.of());

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(20, 10);

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

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10);

		assertThat(users).extracting(User::userId, User::roles)
			.containsExactlyInAnyOrder(tuple(UUID.fromString(LUFFY_ID), List.of("ADMIN")),
					tuple(UUID.fromString(NAMI_ID), List.of("EDITOR", "REVIEWER")));
	}

	@Test
	void filtersOutTheAutoAssignedDefaultRealmRole() {
		UserRepresentation luffy = userWithId(LUFFY_ID);
		when(this.usersResource.list(0, 10)).thenReturn(List.of(luffy));
		mockRoles(LUFFY_ID, "ADMIN", "default-roles-onepiece");

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10);

		assertThat(users.getFirst().roles()).containsExactly("ADMIN");
	}

	@Test
	void countsTheRealmsTotalUsers() {
		when(this.usersResource.count()).thenReturn(37);

		assertThat(this.keycloakUserDirectoryAdapter.countUsers()).isEqualTo(37L);
	}

	@Test
	void wrapsAKeycloakFailureWhenListingUsers() {
		when(this.usersResource.list(0, 10)).thenThrow(new RuntimeException("Keycloak unreachable"));

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.findUsers(0, 10))
			.isInstanceOf(KeycloakCommunicationException.class)
			.cause()
			.hasMessage("Keycloak unreachable");
	}

	@Test
	void wrapsAKeycloakFailureWhenCountingUsers() {
		when(this.usersResource.count()).thenThrow(new RuntimeException("Keycloak unreachable"));

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.countUsers())
			.isInstanceOf(KeycloakCommunicationException.class)
			.cause()
			.hasMessage("Keycloak unreachable");
	}

	@Test
	void wrapsAKeycloakFailureWhenCreatingTheAccountItself() {
		when(this.usersResource.create(any())).thenThrow(new RuntimeException("Keycloak unreachable"));
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.inviteUser(INVITED_EMAIL, roles))
			.isInstanceOf(KeycloakCommunicationException.class)
			.hasMessageContaining(INVITED_EMAIL)
			.cause()
			.hasMessage("Keycloak unreachable");
	}

	@Test
	void wrapsAKeycloakFailureWhenRollingBackAnInvitation() {
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);
		doThrow(new RuntimeException("Keycloak unreachable")).when(userResource).remove();

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.rollbackInvitation(UUID.fromString(NAMI_ID)))
			.isInstanceOf(KeycloakCommunicationException.class)
			.cause()
			.hasMessage("Keycloak unreachable");
	}

	@Test
	void invitesAUserWithNoUsableCredentialAndTheChosenRoles() {
		var response = Response.status(Response.Status.CREATED)
			.location(URI.create("http://keycloak/admin/realms/onepiece/users/" + NAMI_ID))
			.build();
		when(this.usersResource.create(this.newUserCaptor.capture())).thenReturn(response);
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);
		var roleMappingResource = mock(RoleMappingResource.class);
		var realmRoleScopeResource = mock(RoleScopeResource.class);
		when(userResource.roles()).thenReturn(roleMappingResource);
		when(roleMappingResource.realmLevel()).thenReturn(realmRoleScopeResource);
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		RoleRepresentation editorRole = mockRoleRepresentation(rolesResource, "EDITOR");
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);

		User invited = this.keycloakUserDirectoryAdapter.inviteUser(INVITED_EMAIL, roles);

		UserRepresentation createdUser = this.newUserCaptor.getValue();
		assertThat(createdUser.getUsername()).isEqualTo(INVITED_EMAIL);
		assertThat(createdUser.getEmail()).isEqualTo(INVITED_EMAIL);
		assertThat(createdUser.isEnabled()).isTrue();
		assertThat(createdUser.getRequiredActions()).containsExactly("UPDATE_PASSWORD", "VERIFY_EMAIL");
		verify(realmRoleScopeResource).add(List.of(editorRole));
		verify(userResource).executeActionsEmail("onepiece-proxy", "http://localhost:4180/",
				List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
		assertThat(invited.userId()).isEqualTo(UUID.fromString(NAMI_ID));
		assertThat(invited.email()).isEqualTo(INVITED_EMAIL);
		assertThat(invited.status()).isEqualTo(AccountStatus.PENDING);
		assertThat(invited.roles()).containsExactly("EDITOR");
	}

	@Test
	void rollsBackTheCreatedUserWhenRoleAssignmentFails() {
		var response = Response.status(Response.Status.CREATED)
			.location(URI.create("http://keycloak/admin/realms/onepiece/users/" + NAMI_ID))
			.build();
		when(this.usersResource.create(any())).thenReturn(response);
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		when(rolesResource.get("EDITOR")).thenThrow(new RuntimeException("Keycloak unreachable"));
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.inviteUser(INVITED_EMAIL, roles))
			.isInstanceOf(KeycloakCommunicationException.class)
			.hasMessageContaining(INVITED_EMAIL)
			.cause()
			.hasMessage("Keycloak unreachable");

		verify(userResource).remove();
	}

	@Test
	void rollbackInvitationRemovesTheKeycloakUser() {
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);

		this.keycloakUserDirectoryAdapter.rollbackInvitation(UUID.fromString(NAMI_ID));

		verify(userResource).remove();
	}

	@Test
	void refusesToInviteAnAlreadyRegisteredEmail() {
		var response = Response.status(Response.Status.CONFLICT).build();
		when(this.usersResource.create(this.newUserCaptor.capture())).thenReturn(response);
		Set<RealmRole> roles = Set.of(RealmRole.ADMIN);

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.inviteUser(INVITED_EMAIL, roles))
			.isInstanceOf(EmailAlreadyRegisteredException.class);

		verify(this.usersResource, never()).get(any());
	}

	private RoleRepresentation mockRoleRepresentation(RolesResource rolesResource, String roleName) {
		var roleResource = mock(RoleResource.class);
		var representation = new RoleRepresentation(roleName, null, false);
		when(rolesResource.get(roleName)).thenReturn(roleResource);
		when(roleResource.toRepresentation()).thenReturn(representation);
		return representation;
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
