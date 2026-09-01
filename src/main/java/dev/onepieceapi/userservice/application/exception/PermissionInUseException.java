package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;

/**
 * Raised when deleting a permission still held by at least one role - it must be revoked
 * from every role first, mirroring how an in-use role is protected from deletion
 * ({@link RoleInUseException}).
 */
public class PermissionInUseException extends ConflictException {

	public PermissionInUseException(String key) {
		super(UserErrorCode.PERMISSION_IN_USE, "Permission " + key + " is still assigned to at least one role");
	}

}
