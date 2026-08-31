package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;

/**
 * Raised when creating a role names one that already exists. Deliberately
 * provider-agnostic: {@code KeycloakRoleDirectoryAdapter} translates Keycloak's own 409
 * into this before it crosses the port boundary.
 */
public class RoleAlreadyExistsException extends ConflictException {

	public RoleAlreadyExistsException(String name) {
		super(UserErrorCode.ROLE_ALREADY_EXISTS, "A role named " + name + " already exists");
	}

}
