package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;

/**
 * Raised when creating a permission whose key already exists in the catalog.
 */
public class PermissionAlreadyExistsException extends ConflictException {

	public PermissionAlreadyExistsException(String key) {
		super(UserErrorCode.PERMISSION_ALREADY_EXISTS, "A permission named " + key + " already exists");
	}

}
