package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.NotFoundException;

/**
 * Raised when assigning or revoking a permission key the catalog doesn't have.
 */
public class PermissionNotFoundException extends NotFoundException {

	public PermissionNotFoundException(String key) {
		super(UserErrorCode.PERMISSION_NOT_FOUND, "No permission named " + key);
	}

}
