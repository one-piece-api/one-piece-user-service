package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when an admin operation targets a {@code userId} with no matching identity
 * provider account. Deliberately provider-agnostic: {@code KeycloakUserDirectoryAdapter}
 * translates Keycloak's own 404 into this before it crosses the port boundary.
 */
public class UserNotFoundException extends NotFoundException {

	public UserNotFoundException(UUID userId) {
		super(UserErrorCode.NOT_FOUND, "No user found for " + userId);
	}

}
