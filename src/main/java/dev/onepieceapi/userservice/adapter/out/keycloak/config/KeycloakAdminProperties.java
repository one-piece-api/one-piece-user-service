package dev.onepieceapi.userservice.adapter.out.keycloak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * {@code excludedRealmRoles} lists realm roles never shown as a "product role" (e.g.
 * Keycloak's own auto-assigned {@code default-roles-<realm>} composite) - configured here
 * rather than hardcoded as a naming-convention prefix, since the application shouldn't
 * assume how the identity provider names its internal roles.
 */
@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(String serverUrl, String realm, String clientId, String clientSecret,
		Set<String> excludedRealmRoles) {

}
