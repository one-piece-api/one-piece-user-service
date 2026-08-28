package dev.onepieceapi.userservice.adapter.in.web.dto;

import java.util.List;

/**
 * One realm role and the permissions it currently bundles - see
 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}. Read-only: this
 * application never writes a role's permission set, only reflects Keycloak's.
 */
public record RolePermissionsResponse(String role, List<String> permissions) {

}
