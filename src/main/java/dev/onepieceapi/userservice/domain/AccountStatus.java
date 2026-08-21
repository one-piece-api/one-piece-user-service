package dev.onepieceapi.userservice.domain;

/**
 * Lifecycle state of an application user, per §10 of
 * {@code application-user-identity-management.md}: PENDING (invited, not yet activated),
 * ACTIVE (can authenticate), DISABLED (access revoked, can return to ACTIVE on
 * reactivation).
 */
public enum AccountStatus {

	PENDING, ACTIVE, DISABLED

}
