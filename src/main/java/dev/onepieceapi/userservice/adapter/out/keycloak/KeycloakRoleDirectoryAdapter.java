package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakAdminProperties;
import dev.onepieceapi.userservice.application.exception.LastRoleManagerException;
import dev.onepieceapi.userservice.application.exception.PermissionAlreadyExistsException;
import dev.onepieceapi.userservice.application.exception.PermissionInUseException;
import dev.onepieceapi.userservice.application.exception.PermissionNotFoundException;
import dev.onepieceapi.userservice.application.exception.RoleAlreadyExistsException;
import dev.onepieceapi.userservice.application.exception.RoleInUseException;
import dev.onepieceapi.userservice.application.exception.RoleNotFoundException;
import dev.onepieceapi.userservice.application.port.out.RoleDirectoryPort;
import dev.onepieceapi.userservice.domain.PermissionDefinition;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keycloak-backed implementation of {@link RoleDirectoryPort} - the role/permission
 * catalog itself, not a user identity (see {@link KeycloakUserDirectoryAdapter}). Realm
 * roles back "roles", client roles on {@link #PERMISSIONS_CLIENT_ID} back "permissions",
 * and a role's composite client-roles are the permissions it currently bundles - the same
 * mechanism {@code ApplicationUserJwtAuthenticationConverter} reads back out of the JWT
 * (see {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}).
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class KeycloakRoleDirectoryAdapter implements RoleDirectoryPort {

	/** The Keycloak client whose roles this application treats as permissions. */
	private static final String PERMISSIONS_CLIENT_ID = "onepiece-proxy";

	/**
	 * Gates this catalog itself - see
	 * {@code docs/adr/0012-role-permission-catalog-management.md}. A role holding it (and
	 * being the only one that does) can't be deleted or have the permission revoked, the
	 * same "don't lock everyone out" protection {@code LastAdministratorException} gives
	 * the ADMIN role.
	 */
	private static final String MANAGE_PERMISSION = "roles:manage";

	private final Keycloak keycloakAdminClient;

	private final KeycloakAdminProperties keycloakAdminProperties;

	@Override
	public Map<String, List<String>> listRoles() {
		try {
			RolesResource realmRoles = getRealm().roles();
			return roleNames(realmRoles)
				.collect(Collectors.toMap(name -> name, name -> permissionsOf(realmRoles, name)));
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to list roles from Keycloak", ex);
		}
	}

	@Override
	public List<PermissionDefinition> listPermissions() {
		try {
			return permissionsClientRoles().list()
				.stream()
				.map(role -> new PermissionDefinition(role.getName(), role.getDescription()))
				.sorted(Comparator.comparing(PermissionDefinition::key))
				.toList();
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to list permissions from Keycloak", ex);
		}
	}

	@Override
	public Map<String, List<String>> createRole(String name, Optional<String> copyPermissionsFromRole) {
		RolesResource realmRoles = getRealm().roles();
		List<RoleRepresentation> composites = copyPermissionsFromRole.isPresent()
				? compositesOf(realmRoles, copyPermissionsFromRole.get()) : List.of();

		var representation = new RoleRepresentation();
		representation.setName(name);
		try {
			realmRoles.create(representation);
		}
		catch (WebApplicationException ex) {
			if (ex.getResponse().getStatus() == Response.Status.CONFLICT.getStatusCode()) {
				throw new RoleAlreadyExistsException(name);
			}
			throw new KeycloakCommunicationException("Failed to create role " + name, ex);
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to create role " + name, ex);
		}

		if (!composites.isEmpty()) {
			try {
				realmRoles.get(name).addComposites(composites);
			}
			catch (RuntimeException ex) {
				String message = "Failed to copy permissions onto role " + name;
				throw new KeycloakCommunicationException(message, ex);
			}
		}
		return listRoles();
	}

	@Override
	public void deleteRole(String name) {
		RolesResource realmRoles = getRealm().roles();
		RoleResource roleResource = requireRole(realmRoles, name);

		if (!roleResource.getUserMembers(0, 1).isEmpty()) {
			throw new RoleInUseException(name);
		}
		List<String> managers = rolesWithPermission(realmRoles, MANAGE_PERMISSION);
		if (managers.contains(name) && managers.size() <= 1) {
			throw new LastRoleManagerException(name);
		}

		try {
			roleResource.remove();
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to delete role " + name, ex);
		}
	}

	@Override
	public PermissionDefinition createPermission(String key, String description) {
		var representation = new RoleRepresentation();
		representation.setName(key);
		representation.setDescription(description);
		representation.setClientRole(true);
		try {
			permissionsClientRoles().create(representation);
		}
		catch (WebApplicationException ex) {
			if (ex.getResponse().getStatus() == Response.Status.CONFLICT.getStatusCode()) {
				throw new PermissionAlreadyExistsException(key);
			}
			throw new KeycloakCommunicationException("Failed to create permission " + key, ex);
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to create permission " + key, ex);
		}
		return new PermissionDefinition(key, description);
	}

	@Override
	public void assignPermission(String role, String permissionKey) {
		RolesResource realmRoles = getRealm().roles();
		RoleResource roleResource = requireRole(realmRoles, role);
		RoleRepresentation permission = requirePermissionRepresentation(permissionKey);
		if (permissionsOf(realmRoles, role).contains(permissionKey)) {
			return;
		}

		try {
			roleResource.addComposites(List.of(permission));
		}
		catch (RuntimeException ex) {
			String message = "Failed to assign " + permissionKey + " to role " + role;
			throw new KeycloakCommunicationException(message, ex);
		}
	}

	@Override
	public void revokePermission(String role, String permissionKey) {
		RolesResource realmRoles = getRealm().roles();
		RoleResource roleResource = requireRole(realmRoles, role);
		RoleRepresentation permission = requirePermissionRepresentation(permissionKey);
		if (!permissionsOf(realmRoles, role).contains(permissionKey)) {
			return;
		}
		boolean isLastManager = rolesWithPermission(realmRoles, MANAGE_PERMISSION).size() <= 1;
		if (MANAGE_PERMISSION.equals(permissionKey) && isLastManager) {
			throw new LastRoleManagerException(role);
		}

		try {
			roleResource.deleteComposites(List.of(permission));
		}
		catch (RuntimeException ex) {
			String message = "Failed to revoke " + permissionKey + " from role " + role;
			throw new KeycloakCommunicationException(message, ex);
		}
	}

	@Override
	public void deletePermission(String key) {
		requirePermissionRepresentation(key);
		RolesResource realmRoles = getRealm().roles();
		if (!rolesWithPermission(realmRoles, key).isEmpty()) {
			throw new PermissionInUseException(key);
		}

		try {
			permissionsClientRoles().get(key).remove();
		}
		catch (RuntimeException ex) {
			throw new KeycloakCommunicationException("Failed to delete permission " + key, ex);
		}
	}

	private Stream<String> roleNames(RolesResource realmRoles) {
		return realmRoles.list()
			.stream()
			.map(RoleRepresentation::getName)
			.filter(name -> !this.keycloakAdminProperties.excludedRealmRoles().contains(name));
	}

	private List<String> rolesWithPermission(RolesResource realmRoles, String permissionKey) {
		Stream<String> names = roleNames(realmRoles);
		return names.filter(name -> permissionsOf(realmRoles, name).contains(permissionKey)).toList();
	}

	private List<String> permissionsOf(RolesResource realmRoles, String roleName) {
		return realmRoles.get(roleName)
			.getRoleComposites()
			.stream()
			.filter(RoleRepresentation::getClientRole)
			.map(RoleRepresentation::getName)
			.sorted()
			.toList();
	}

	private List<RoleRepresentation> compositesOf(RolesResource realmRoles, String roleName) {
		try {
			return List.copyOf(realmRoles.get(roleName).getRoleComposites());
		}
		catch (NotFoundException ex) {
			throw new RoleNotFoundException(roleName);
		}
	}

	private RoleResource requireRole(RolesResource realmRoles, String name) {
		RoleResource roleResource = realmRoles.get(name);
		try {
			roleResource.toRepresentation();
		}
		catch (NotFoundException ex) {
			throw new RoleNotFoundException(name);
		}
		return roleResource;
	}

	private RoleRepresentation requirePermissionRepresentation(String key) {
		try {
			return permissionsClientRoles().get(key).toRepresentation();
		}
		catch (NotFoundException ex) {
			throw new PermissionNotFoundException(key);
		}
	}

	private RolesResource permissionsClientRoles() {
		List<ClientRepresentation> clients = getRealm().clients().findByClientId(PERMISSIONS_CLIENT_ID);
		if (clients.isEmpty()) {
			String message = "Keycloak client " + PERMISSIONS_CLIENT_ID + " not found";
			throw new KeycloakCommunicationException(message, null);
		}
		return getRealm().clients().get(clients.getFirst().getId()).roles();
	}

	private RealmResource getRealm() {
		return this.keycloakAdminClient.realm(this.keycloakAdminProperties.realm());
	}

}
