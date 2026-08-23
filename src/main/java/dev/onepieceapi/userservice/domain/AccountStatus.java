package dev.onepieceapi.userservice.domain;

/**
 * Conceptual account status (§10 of {@code application-user-identity-management.md}) -
 * never a stored field (see {@link ApplicationUser}), always derived by whichever
 * identity provider adapter implements {@code UserDirectoryPort} from that provider's own
 * account state, rather than computed here: the domain has no knowledge of how any given
 * identity provider represents "enabled" or "has a usable credential".
 */
public enum AccountStatus {

	PENDING, ACTIVE, DISABLED

}
