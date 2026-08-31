package dev.onepieceapi.userservice.application.port.out;

import dev.onepieceapi.userservice.application.exception.LastRoleManagerException;
import dev.onepieceapi.userservice.application.exception.PermissionAlreadyExistsException;
import dev.onepieceapi.userservice.application.exception.PermissionNotFoundException;
import dev.onepieceapi.userservice.application.exception.RoleAlreadyExistsException;
import dev.onepieceapi.userservice.application.exception.RoleInUseException;
import dev.onepieceapi.userservice.application.exception.RoleNotFoundException;
import dev.onepieceapi.userservice.domain.PermissionDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound port for the role/permission catalog itself - a different resource from a user
 * identity ({@link UserDirectoryPort}): roles and permissions exist independently of
 * whether any user currently holds them. See
 * {@code docs/adr/0012-role-permission-catalog-management.md}.
 */
public interface RoleDirectoryPort {

	/**
	 * Every realm role this application manages (Keycloak built-ins such as
	 * {@code default-roles-<realm>} are excluded, see
	 * {@code KeycloakAdminProperties#excludedRealmRoles}) and the permission keys each
	 * currently bundles as Keycloak composite client-roles - see
	 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}.
	 */
	Map<String, List<String>> listRoles();

	/**
	 * Every permission that exists, whether or not any role currently holds it - the
	 * catalog {@link #listRoles} alone cannot show, since an unassigned permission
	 * appears in no role's composite list.
	 */
	List<PermissionDefinition> listPermissions();

	/**
	 * Creates a new realm role, optionally seeded with the same permissions
	 * {@code copyPermissionsFromRole} currently holds - {@code Optional.empty()} creates
	 * it with none. Returns the full, updated role catalog (matching {@link #listRoles}'s
	 * shape) so the caller doesn't need a second round trip to show the result.
	 * @throws RoleAlreadyExistsException if a role named {@code name} already exists
	 * @throws RoleNotFoundException if {@code copyPermissionsFromRole} is present but
	 * names no existing role
	 */
	Map<String, List<String>> createRole(String name, Optional<String> copyPermissionsFromRole);

	/**
	 * @throws RoleNotFoundException if no role named {@code name} exists
	 * @throws RoleInUseException if any user currently holds {@code name}
	 * @throws LastRoleManagerException if {@code name} holds the permission that manages
	 * this catalog and is the only role that does
	 */
	void deleteRole(String name);

	/**
	 * @throws PermissionAlreadyExistsException if {@code key} already exists
	 */
	PermissionDefinition createPermission(String key, String description);

	/**
	 * Idempotent: a role that already holds {@code permissionKey} is left as-is.
	 * @throws RoleNotFoundException if no role named {@code role} exists
	 * @throws PermissionNotFoundException if no permission named {@code permissionKey}
	 * exists
	 */
	void assignPermission(String role, String permissionKey);

	/**
	 * Idempotent: a role that doesn't hold {@code permissionKey} is left as-is.
	 * @throws RoleNotFoundException if no role named {@code role} exists
	 * @throws PermissionNotFoundException if no permission named {@code permissionKey}
	 * exists
	 * @throws LastRoleManagerException if {@code permissionKey} manages this catalog and
	 * {@code role} is the only one that currently holds it
	 */
	void revokePermission(String role, String permissionKey);

}
