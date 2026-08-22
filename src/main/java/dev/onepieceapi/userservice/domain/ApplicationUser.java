package dev.onepieceapi.userservice.domain;

import java.util.UUID;

/**
 * A user's identity (see §2 of {@code application-user-identity-management.md}), read
 * directly from the validated JWT's own claims - the standard {@code sub} claim
 * (Keycloak's own account id) and {@code email} - by
 * {@code ApplicationUserJwtAuthenticationConverter}, with no local persistence. Roles and
 * account status are deliberately not carried here either: Keycloak is the sole owner of
 * all identity data, so authorization reads it from the token / the identity provider
 * directly rather than a local copy that could drift out of sync.
 */
public record ApplicationUser(UUID userId, String email) {

}
