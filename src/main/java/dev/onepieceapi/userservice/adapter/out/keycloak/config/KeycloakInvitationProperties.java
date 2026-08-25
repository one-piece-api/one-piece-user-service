package dev.onepieceapi.userservice.adapter.out.keycloak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The client/redirect Keycloak's {@code execute-actions-email} needs to send the user
 * somewhere after completing the required actions (UF-IDU-01). Identical in every
 * environment - the browser always reaches the app through the same proxy host,
 * regardless of where this process itself runs - so unlike
 * {@code KeycloakAdminProperties} these live directly in {@code application.properties},
 * not per-profile.
 * <p>
 * {@code tokenLifespan} is passed explicitly on every {@code execute-actions-email} call
 * (invite, Step 4; resend, Step 5, UF-IDU-03) instead of relying on the realm's own
 * {@code actionTokenGeneratedByAdminLifespan} setting - this application is then the
 * single source of truth for how long a link it triggers stays valid, with nothing to
 * fetch or keep in sync with a separate Keycloak realm setting when checking whether the
 * current invitation has expired (see {@code KeycloakUserDirectoryAdapter}).
 */
@ConfigurationProperties(prefix = "keycloak.invitation")
public record KeycloakInvitationProperties(String redirectClientId, String redirectUri, Duration tokenLifespan) {

}
