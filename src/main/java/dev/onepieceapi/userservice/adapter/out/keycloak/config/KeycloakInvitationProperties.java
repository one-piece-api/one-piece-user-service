package dev.onepieceapi.userservice.adapter.out.keycloak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The client/redirect Keycloak's {@code execute-actions-email} needs to send the user
 * somewhere after completing the required actions (UF-IDU-01). Identical in every
 * environment - the browser always reaches the app through the same proxy host,
 * regardless of where this process itself runs - so unlike
 * {@code KeycloakAdminProperties} these live directly in {@code application.properties},
 * not per-profile.
 */
@ConfigurationProperties(prefix = "keycloak.invitation")
public record KeycloakInvitationProperties(String redirectClientId, String redirectUri) {

}
