package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;

import java.util.UUID;

/**
 * Raised when revoking the ADMIN role from {@code userId} would leave the realm with zero
 * ADMIN users (UF-IDU-16). Checked in {@code KeycloakUserDirectoryAdapter} against
 * Keycloak's own role membership, not a local count - see
 * {@code docs/adr/0006-role-update-endpoints-and-user-detail-view.md}.
 */
public class LastAdministratorException extends ConflictException {

	public LastAdministratorException(UUID userId) {
		super(UserErrorCode.LAST_ADMINISTRATOR,
				"Cannot remove the ADMIN role from " + userId + " - at least one ADMIN must remain");
	}

}
