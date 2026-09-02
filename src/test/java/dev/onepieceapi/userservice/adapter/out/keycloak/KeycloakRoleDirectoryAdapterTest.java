package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakAdminProperties;
import dev.onepieceapi.userservice.application.exception.LastRoleManagerException;
import dev.onepieceapi.userservice.application.exception.PermissionAlreadyExistsException;
import dev.onepieceapi.userservice.application.exception.PermissionInUseException;
import dev.onepieceapi.userservice.application.exception.PermissionNotFoundException;
import dev.onepieceapi.userservice.application.exception.RoleAlreadyExistsException;
import dev.onepieceapi.userservice.application.exception.RoleInUseException;
import dev.onepieceapi.userservice.application.exception.RoleNotFoundException;
import dev.onepieceapi.userservice.config.KeycloakRoleProperties;
import dev.onepieceapi.userservice.domain.PermissionDefinition;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakRoleDirectoryAdapterTest {

	private static final String PERMISSIONS_CLIENT_UUID = "onepiece-proxy-uuid";

	@Mock
	private Keycloak keycloakAdminClient;

	@Mock
	private RealmResource realmResource;

	@Mock
	private RolesResource realmRoles;

	@Mock
	private ClientsResource clientsResource;

	@Mock
	private ClientResource clientResource;

	@Mock
	private RolesResource permissionRoles;

	@Captor
	private ArgumentCaptor<List<RoleRepresentation>> compositesCaptor;

	private KeycloakRoleDirectoryAdapter adapter;

	@BeforeEach
	void setUp() {
		String clientSecret = "secret";
		var adminProperties = new KeycloakAdminProperties("http://keycloak", "onepiece", "user-service-admin",
				clientSecret);
		var roleProperties = new KeycloakRoleProperties(Set.of("default-roles-onepiece"));
		this.adapter = new KeycloakRoleDirectoryAdapter(this.keycloakAdminClient, adminProperties, roleProperties);

		lenient().when(this.keycloakAdminClient.realm("onepiece")).thenReturn(this.realmResource);
		lenient().when(this.realmResource.roles()).thenReturn(this.realmRoles);
		lenient().when(this.realmResource.clients()).thenReturn(this.clientsResource);
		var clientRepresentation = new ClientRepresentation();
		clientRepresentation.setId(PERMISSIONS_CLIENT_UUID);
		List<ClientRepresentation> clients = List.of(clientRepresentation);
		lenient().when(this.clientsResource.findByClientId("onepiece-proxy")).thenReturn(clients);
		lenient().when(this.clientsResource.get(PERMISSIONS_CLIENT_UUID)).thenReturn(this.clientResource);
		lenient().when(this.clientResource.roles()).thenReturn(this.permissionRoles);
	}

	@Test
	void listsEachRoleAndItsClientRoleCompositesAsPermissions() {
		mockRealmRoleNames("ADMIN", "REVIEWER", "default-roles-onepiece");
		mockComposites("ADMIN", clientRole("users:read"), clientRole("audit:read"));
		mockComposites("REVIEWER", clientRole("docs:read"));

		var permissions = this.adapter.listRoles();

		assertThat(permissions).containsOnlyKeys("ADMIN", "REVIEWER")
			.containsEntry("ADMIN", List.of("audit:read", "users:read"))
			.containsEntry("REVIEWER", List.of("docs:read"));
	}

	@Test
	void ignoresARealmRoleCompositeWhenListingPermissions() {
		mockRealmRoleNames("ADMIN");
		var realmComposite = new RoleRepresentation("default-roles-onepiece", null, true);
		realmComposite.setClientRole(false);
		mockComposites("ADMIN", realmComposite);

		var permissions = this.adapter.listRoles();

		assertThat(permissions.get("ADMIN")).isEmpty();
	}

	@Test
	void listsEveryPermissionInTheClientRegardlessOfAssignment() {
		var users = clientRole("users:read");
		users.setDescription("List and view crew members");
		var approve = clientRole("docs:approve");
		approve.setDescription("Approve documents");
		when(this.permissionRoles.list()).thenReturn(List.of(users, approve));

		List<PermissionDefinition> permissions = this.adapter.listPermissions();

		assertThat(permissions).containsExactly(new PermissionDefinition("docs:approve", "Approve documents"),
				new PermissionDefinition("users:read", "List and view crew members"));
	}

	@Test
	void createsARoleWithNoPermissionsWhenNothingToCopyFrom() {
		mockRealmRoleNames("ADMIN", "NAVIGATOR");
		mockComposites("ADMIN");
		mockComposites("NAVIGATOR");

		var result = this.adapter.createRole("NAVIGATOR", Optional.empty());

		verify(this.realmRoles).create(argThatNamed("NAVIGATOR"));
		assertThat(result).containsKey("NAVIGATOR");
	}

	@Test
	void createsARoleCopyingAnotherRolesPermissions() {
		var sourceRoleResource = mock(RoleResource.class);
		when(this.realmRoles.get("ADMIN")).thenReturn(sourceRoleResource);
		when(sourceRoleResource.getRoleComposites()).thenReturn(Set.of(clientRole("audit:read")));
		var newRoleResource = mock(RoleResource.class);
		when(this.realmRoles.get("NAVIGATOR")).thenReturn(newRoleResource);
		mockRealmRoleNames("ADMIN", "NAVIGATOR");
		when(newRoleResource.getRoleComposites()).thenReturn(Set.of(clientRole("audit:read")));

		this.adapter.createRole("NAVIGATOR", Optional.of("ADMIN"));

		verify(newRoleResource).addComposites(this.compositesCaptor.capture());
		assertThat(this.compositesCaptor.getValue()).extracting(RoleRepresentation::getName)
			.containsExactly("audit:read");
	}

	@Test
	void rejectsCopyingFromARoleThatDoesNotExist() {
		when(this.realmRoles.get("GHOST")).thenThrow(new NotFoundException());

		assertThatThrownBy(() -> this.adapter.createRole("NAVIGATOR", Optional.of("GHOST")))
			.isInstanceOf(RoleNotFoundException.class);
	}

	@Test
	void wrapsAConflictWhenCreatingADuplicateRole() {
		var conflict = new ClientErrorException(Response.Status.CONFLICT);
		doThrow(conflict).when(this.realmRoles).create(argThatNamed("ADMIN"));

		assertThatThrownBy(() -> this.adapter.createRole("ADMIN", Optional.empty()))
			.isInstanceOf(RoleAlreadyExistsException.class);
	}

	@Test
	void deletesARoleWithNoMembers() {
		mockRealmRoleNames("NAVIGATOR");
		var roleResource = mockComposites("NAVIGATOR");
		when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation("NAVIGATOR", null, false));
		when(roleResource.getUserMembers(0, 1)).thenReturn(List.of());

		this.adapter.deleteRole("NAVIGATOR");

		verify(roleResource).remove();
	}

	@Test
	void rejectsDeletingARoleThatDoesNotExist() {
		var roleResource = mock(RoleResource.class);
		when(this.realmRoles.get("GHOST")).thenReturn(roleResource);
		when(roleResource.toRepresentation()).thenThrow(new NotFoundException());

		assertThatThrownBy(() -> this.adapter.deleteRole("GHOST")).isInstanceOf(RoleNotFoundException.class);
	}

	@Test
	void rejectsDeletingARoleStillHeldByAUser() {
		var roleResource = mock(RoleResource.class);
		when(this.realmRoles.get("EDITOR")).thenReturn(roleResource);
		when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation("EDITOR", null, false));
		when(roleResource.getUserMembers(0, 1)).thenReturn(List.of(new UserRepresentation()));

		assertThatThrownBy(() -> this.adapter.deleteRole("EDITOR")).isInstanceOf(RoleInUseException.class);
		verify(roleResource, never()).remove();
	}

	@Test
	void rejectsDeletingTheLastRoleThatCanManageTheCatalog() {
		mockRealmRoleNames("ADMIN", "EDITOR");
		var roleResource = mockComposites("ADMIN", clientRole("roles:manage"));
		when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation("ADMIN", null, false));
		when(roleResource.getUserMembers(0, 1)).thenReturn(List.of());
		mockComposites("EDITOR", clientRole("docs:write"));

		assertThatThrownBy(() -> this.adapter.deleteRole("ADMIN")).isInstanceOf(LastRoleManagerException.class);
		verify(roleResource, never()).remove();
	}

	@Test
	void createsAPermission() {
		var created = this.adapter.createPermission("docs:approve", "Approve documents");

		assertThat(created).isEqualTo(new PermissionDefinition("docs:approve", "Approve documents"));
		verify(this.permissionRoles).create(argThatNamed("docs:approve"));
	}

	@Test
	void wrapsAConflictWhenCreatingADuplicatePermission() {
		doThrow(new ClientErrorException(Response.Status.CONFLICT)).when(this.permissionRoles)
			.create(argThatNamed("users:read"));

		assertThatThrownBy(() -> this.adapter.createPermission("users:read", "dup"))
			.isInstanceOf(PermissionAlreadyExistsException.class);
	}

	@Test
	void assignsAPermissionNotYetHeldByTheRole() {
		var roleResource = mock(RoleResource.class);
		when(this.realmRoles.get("EDITOR")).thenReturn(roleResource);
		when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation("EDITOR", null, false));
		var permissionResource = mock(RoleResource.class);
		when(this.permissionRoles.get("docs:approve")).thenReturn(permissionResource);
		var permissionRepresentation = clientRole("docs:approve");
		when(permissionResource.toRepresentation()).thenReturn(permissionRepresentation);
		when(roleResource.getRoleComposites()).thenReturn(Set.of());

		this.adapter.assignPermission("EDITOR", "docs:approve");

		verify(roleResource).addComposites(List.of(permissionRepresentation));
	}

	@Test
	void assigningAnAlreadyHeldPermissionIsANoOp() {
		var roleResource = mock(RoleResource.class);
		when(this.realmRoles.get("EDITOR")).thenReturn(roleResource);
		when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation("EDITOR", null, false));
		var permissionResource = mock(RoleResource.class);
		when(this.permissionRoles.get("docs:approve")).thenReturn(permissionResource);
		when(permissionResource.toRepresentation()).thenReturn(clientRole("docs:approve"));
		when(roleResource.getRoleComposites()).thenReturn(Set.of(clientRole("docs:approve")));

		this.adapter.assignPermission("EDITOR", "docs:approve");

		verify(roleResource, never()).addComposites(any());
	}

	@Test
	void rejectsAssigningAPermissionThatDoesNotExist() {
		var roleResource = mock(RoleResource.class);
		when(this.realmRoles.get("EDITOR")).thenReturn(roleResource);
		when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation("EDITOR", null, false));
		var permissionResource = mock(RoleResource.class);
		when(this.permissionRoles.get("ghost:key")).thenReturn(permissionResource);
		when(permissionResource.toRepresentation()).thenThrow(new NotFoundException());

		assertThatThrownBy(() -> this.adapter.assignPermission("EDITOR", "ghost:key"))
			.isInstanceOf(PermissionNotFoundException.class);
	}

	@Test
	void revokingAPermissionNotHeldIsANoOp() {
		var roleResource = mock(RoleResource.class);
		when(this.realmRoles.get("EDITOR")).thenReturn(roleResource);
		when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation("EDITOR", null, false));
		var permissionResource = mock(RoleResource.class);
		when(this.permissionRoles.get("docs:approve")).thenReturn(permissionResource);
		when(permissionResource.toRepresentation()).thenReturn(clientRole("docs:approve"));
		when(roleResource.getRoleComposites()).thenReturn(Set.of());

		this.adapter.revokePermission("EDITOR", "docs:approve");

		verify(roleResource, never()).deleteComposites(any());
	}

	@Test
	void rejectsRevokingTheLastManagePermissionFromItsOnlyHolder() {
		mockRealmRoleNames("ADMIN", "EDITOR");
		var roleResource = mockComposites("ADMIN", clientRole("roles:manage"));
		when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation("ADMIN", null, false));
		mockComposites("EDITOR", clientRole("docs:write"));
		var permissionResource = mock(RoleResource.class);
		when(this.permissionRoles.get("roles:manage")).thenReturn(permissionResource);
		when(permissionResource.toRepresentation()).thenReturn(clientRole("roles:manage"));

		assertThatThrownBy(() -> this.adapter.revokePermission("ADMIN", "roles:manage"))
			.isInstanceOf(LastRoleManagerException.class);
		verify(roleResource, never()).deleteComposites(any());
	}

	@Test
	void deletesAPermissionHeldByNoRole() {
		mockRealmRoleNames("ADMIN");
		mockComposites("ADMIN");
		var permissionResource = mock(RoleResource.class);
		when(this.permissionRoles.get("docs:approve")).thenReturn(permissionResource);
		when(permissionResource.toRepresentation()).thenReturn(clientRole("docs:approve"));

		this.adapter.deletePermission("docs:approve");

		verify(permissionResource).remove();
	}

	@Test
	void rejectsDeletingAPermissionThatDoesNotExist() {
		var permissionResource = mock(RoleResource.class);
		when(this.permissionRoles.get("ghost:key")).thenReturn(permissionResource);
		when(permissionResource.toRepresentation()).thenThrow(new NotFoundException());

		assertThatThrownBy(() -> this.adapter.deletePermission("ghost:key"))
			.isInstanceOf(PermissionNotFoundException.class);
	}

	@Test
	void rejectsDeletingAPermissionStillHeldByARole() {
		mockRealmRoleNames("ADMIN");
		var roleResource = mockComposites("ADMIN", clientRole("docs:approve"));
		var permissionResource = mock(RoleResource.class);
		when(this.permissionRoles.get("docs:approve")).thenReturn(permissionResource);
		when(permissionResource.toRepresentation()).thenReturn(clientRole("docs:approve"));

		assertThatThrownBy(() -> this.adapter.deletePermission("docs:approve"))
			.isInstanceOf(PermissionInUseException.class);
		verify(permissionResource, never()).remove();
		verify(roleResource, never()).deleteComposites(any());
	}

	private void mockRealmRoleNames(String... names) {
		List<RoleRepresentation> roles = List.of(names)
			.stream()
			.map(name -> new RoleRepresentation(name, null, false))
			.toList();
		when(this.realmRoles.list()).thenReturn(roles);
	}

	private RoleResource mockComposites(String roleName, RoleRepresentation... composites) {
		var roleResource = mock(RoleResource.class);
		lenient().when(this.realmRoles.get(roleName)).thenReturn(roleResource);
		when(roleResource.getRoleComposites()).thenReturn(Set.of(composites));
		return roleResource;
	}

	private static RoleRepresentation clientRole(String name) {
		var representation = new RoleRepresentation(name, null, false);
		representation.setClientRole(true);
		return representation;
	}

	private static RoleRepresentation argThatNamed(String name) {
		return argThat(representation -> representation != null && name.equals(representation.getName()));
	}

}
