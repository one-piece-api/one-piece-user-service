package dev.onepieceapi.userservice.adapter.out.keycloak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(String serverUrl, String realm, String clientId, String clientSecret) {

}
