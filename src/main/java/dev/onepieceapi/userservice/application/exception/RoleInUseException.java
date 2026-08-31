package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;

/**
 * Raised when deleting a role still held by at least one user - the role must be moved
 * off every member first, mirroring how an in-use resource is protected elsewhere in this
 * service (e.g. {@link LastRoleException}).
 */
public class RoleInUseException extends ConflictException {

	public RoleInUseException(String name) {
		super(UserErrorCode.ROLE_IN_USE, "Role " + name + " is still assigned to at least one user");
	}

}
