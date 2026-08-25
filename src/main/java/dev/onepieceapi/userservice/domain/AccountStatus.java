package dev.onepieceapi.userservice.domain;

/**
 * Conceptual account status (§10 of {@code application-user-identity-management.md}) -
 * never persisted locally, always derived from whichever identity provider adapter
 * implements {@code UserDirectoryPort} - either from the token's own claims or from that
 * provider's live account state (see {@link User}'s javadoc for the difference), rather
 * than computed here: the domain has no knowledge of how any given identity provider
 * represents "enabled" or "has a usable credential".
 * <p>
 * {@link #INVITATION_EXPIRED} (Step 5, UF-IDU-03) is a refinement of {@link #PENDING},
 * not a separate lifecycle stage: the Keycloak account itself is unchanged (still
 * enabled, no usable credential) - only whether the currently outstanding invitation link
 * is still within its own validity window differs. Resending is only meaningful, and only
 * allowed, once a PENDING account reaches this state - see
 * {@code KeycloakUserDirectoryAdapter#resendInvitation}.
 */
public enum AccountStatus {

	PENDING, INVITATION_EXPIRED, ACTIVE, DISABLED

}
