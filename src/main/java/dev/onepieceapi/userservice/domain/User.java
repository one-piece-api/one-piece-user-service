package dev.onepieceapi.userservice.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An application-user identity, sourced entirely from the identity provider behind
 * {@code UserDirectoryPort} - never persisted locally (see §2 of
 * {@code application-user-identity-management.md}). One shape, two different ways to
 * build it depending on who is being asked about:
 * <ul>
 * <li>the caller's own identity ({@code ApplicationUserJwtAuthenticationConverter}),
 * resolved from the validated JWT's own claims with no lookup - {@code status} is always
 * {@link AccountStatus#ACTIVE} there: holding a validly-signed, unexpired token already
 * implies the account was active enough to authenticate (a PENDING account has no usable
 * credential to get one with; Keycloak refuses to refresh a token for a since-disabled
 * one, per §12 - see the converter's own javadoc), and {@code createdAt} is unavailable
 * (not a token claim), so left {@code null};</li>
 * <li>someone else's identity, e.g. the admin listing (UF-IDU-17) or an invitation
 * (UF-IDU-01) - resolved from Keycloak's Admin API
 * ({@code KeycloakUserDirectoryAdapter}), which is the only place {@code status} reflects
 * the account's live state.</li>
 * </ul>
 */
public record User(UUID userId, String username, String email, AccountStatus status, List<String> roles,
		Instant createdAt) {
}
