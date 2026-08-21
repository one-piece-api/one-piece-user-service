package dev.onepieceapi.userservice.domain;

import java.util.UUID;

/**
 * The application-owned half of a user's identity (see §2 of
 * {@code application-user-identity-management.md}): the fields that have no
 * identity-provider equivalent. Account status and email verification are deliberately
 * not carried here either, for the same reason as roles: Keycloak is the sole owner of
 * that data and authorization reads it from the token / the identity provider directly,
 * rather than a local copy that could drift out of sync (see
 * {@code ApplicationUserJwtAuthenticationConverter}).
 * <p>
 * This is the domain representation, decoupled from the persistence entity's JPA mapping
 * ({@code ApplicationUserEntity}): business and security code depends on this record,
 * never on the entity. The domain layer has no dependency on persistence — the mapping
 * between the two lives in {@code ApplicationUserMapper} instead.
 */
public record ApplicationUser(UUID userId, String email) {

}
