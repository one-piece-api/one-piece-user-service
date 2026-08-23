package dev.onepieceapi.userservice.domain;

/**
 * Conceptual account status (§10 of {@code application-user-identity-management.md}) -
 * never persisted locally, always derived from whichever identity provider adapter
 * implements {@code UserDirectoryPort} - either from the token's own claims or from that
 * provider's live account state (see {@link User}'s javadoc for the difference), rather
 * than computed here: the domain has no knowledge of how any given identity provider
 * represents "enabled" or "has a usable credential".
 */
public enum AccountStatus {

	PENDING, ACTIVE, DISABLED

}
