package dev.onepieceapi.userservice.domain;

/**
 * Identity/account events worth an audit trail (§13 of
 * {@code application-user-identity-management.md}). One value per mutating use case,
 * added as each one is implemented - not pre-declared ahead of the step that needs it.
 */
public enum AuditAction {

	USER_INVITED

}
