package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ValidationException;

/**
 * Raised when a new role's name, after normalization (uppercase, non-alphanumeric
 * collapsed to {@code _}), has nothing left - e.g. a name made entirely of punctuation.
 */
public class InvalidRoleNameException extends ValidationException {

	public InvalidRoleNameException(String name) {
		super(UserErrorCode.INVALID_ROLE_NAME, message(name));
	}

	private static String message(String name) {
		return "\"" + name + "\" leaves no usable role name after normalization";
	}

}
