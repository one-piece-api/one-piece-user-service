package dev.onepieceapi.userservice.exception;

import java.util.UUID;

/**
 * No {@code ApplicationUser} exists for the given id. Thrown by
 * {@code ApplicationUserService} so each caller translates a missing user into whatever
 * response fits its own context — {@link GlobalExceptionHandler} maps it to an HTTP 404
 * for a direct lookup, while {@code ApplicationUserJwtAuthenticationConverter} maps it to
 * a 401 for a JWT that no longer resolves to a known user.
 */
public class ApplicationUserNotFoundException extends RuntimeException {

	public ApplicationUserNotFoundException(UUID userId) {
		super("No application user found for id " + userId);
	}

}
