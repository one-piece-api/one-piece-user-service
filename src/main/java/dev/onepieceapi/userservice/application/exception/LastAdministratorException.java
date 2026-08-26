package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;

import java.util.UUID;

/**
 * Raised when an operation on {@code userId} would leave the realm with zero ADMIN users
 * (UF-IDU-16: "role change or account disable") - either revoking their ADMIN role or
 * revoking access (disabling their account) entirely. Checked in
 * {@code KeycloakUserDirectoryAdapter} against Keycloak's own role membership, not a
 * local count - see {@code docs/adr/0006-role-update-endpoints-and-user-detail-view.md}.
 */
public class LastAdministratorException extends ConflictException {

	public LastAdministratorException(UUID userId) {
		super(UserErrorCode.LAST_ADMINISTRATOR,
				"Cannot leave the realm with zero ADMIN users - " + userId + " is the last one");
	}

}
