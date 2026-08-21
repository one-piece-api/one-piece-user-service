package dev.onepieceapi.userservice.domain;

import java.util.UUID;

/**
 * The application-owned half of a user's identity (see §2 of
 * {@code application-user-identity-management.md}): email and account status as the
 * application currently considers them, independent of what a given already-issued token
 * happens to carry — status must be checked so a revocation blocks even a still-valid
 * token immediately (UF-IDU-10, UF-IDU-13). Roles are deliberately not carried here:
 * Keycloak recomputes them fresh on every token issuance, so authorization reads them
 * from the token instead (see {@code ApplicationUserJwtAuthenticationConverter}).
 * <p>
 * This is the domain representation, decoupled from the persistence entity's JPA mapping
 * ({@code ApplicationUserEntity}): business and security code depends on this record,
 * never on the entity. The domain layer has no dependency on persistence — the mapping
 * between the two lives in {@code ApplicationUserMapper} instead.
 */
public record ApplicationUser(UUID userId, String email, AccountStatus status) {

	public String statusName() {
		return status != null ? status.name() : null;
	}

}
