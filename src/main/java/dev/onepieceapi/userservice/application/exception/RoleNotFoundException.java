package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.NotFoundException;

/**
 * Raised when an operation names a role that doesn't exist - either assigning/revoking it
 * on a user (roles lost the compile-time enum validation they used to get for free once
 * they became dynamic, see {@code docs/adr/0012-role-permission-catalog-management.md})
 * or managing the role catalog itself (delete, copy-from on create).
 */
public class RoleNotFoundException extends NotFoundException {

	public RoleNotFoundException(String name) {
		super(UserErrorCode.ROLE_NOT_FOUND, "No role named " + name);
	}

}
