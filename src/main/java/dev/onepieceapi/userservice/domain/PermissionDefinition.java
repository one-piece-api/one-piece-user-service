package dev.onepieceapi.userservice.domain;

/**
 * One entry in the permission catalog - a Keycloak client role on the SPA's own client
 * (see {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}), independent of
 * whether any realm role currently holds it. {@code key} is the {@code resource:action}
 * string used everywhere else a permission is referenced (e.g. {@code Permission}); this
 * pairs it with the human-readable description an admin gave it at creation.
 */
public record PermissionDefinition(String key, String description) {
}
