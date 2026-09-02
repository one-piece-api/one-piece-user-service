package dev.onepieceapi.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * {@code excludedRealmRoles} lists realm roles never treated as a "product role" (e.g.
 * Keycloak's own auto-assigned {@code default-roles-<realm>} composite) - configured here
 * rather than hardcoded as a naming-convention prefix, since the application shouldn't
 * assume how the identity provider names its internal roles. Shared by every place a
 * Keycloak realm role list is surfaced: the Admin API adapters ({@code
 * KeycloakUserDirectoryAdapter}, {@code KeycloakRoleDirectoryAdapter}) and the JWT-based
 * {@code ApplicationUserJwtAuthenticationConverter} - kept independent of {@code
 * KeycloakAdminProperties} since the latter is Admin REST client connection detail
 * (server/realm/credentials) that the JWT converter has no need to depend on.
 */
@ConfigurationProperties(prefix = "keycloak.roles")
public record KeycloakRoleProperties(Set<String> excludedRealmRoles) {

}
