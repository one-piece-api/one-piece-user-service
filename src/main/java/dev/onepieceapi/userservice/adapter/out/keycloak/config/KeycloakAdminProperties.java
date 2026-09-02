package dev.onepieceapi.userservice.adapter.out.keycloak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection details for the Keycloak Admin REST client - see
 * {@code KeycloakAdminConfig}.
 */
@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(String serverUrl, String realm, String clientId, String clientSecret) {

}
