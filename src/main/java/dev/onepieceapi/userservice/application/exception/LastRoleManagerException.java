package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;

/**
 * Raised when deleting a role, or revoking its {@code roles:manage} permission, would
 * leave no role able to manage the role/permission catalog at all - the same
 * "last-of-its-kind" protection {@link LastAdministratorException} gives the ADMIN role,
 * applied here to whichever role(s) currently hold {@code roles:manage} instead of a
 * hardcoded role name, since which role that is can itself change now that roles are
 * dynamic. See {@code docs/adr/0012-role-permission-catalog-management.md}.
 */
public class LastRoleManagerException extends ConflictException {

	public LastRoleManagerException(String role) {
		super(UserErrorCode.LAST_ROLE_MANAGER, message(role));
	}

	private static String message(String role) {
		String reason = "Cannot leave the realm with no role able to manage roles/permissions";
		return reason + " - " + role + " is the last one";
	}

}
