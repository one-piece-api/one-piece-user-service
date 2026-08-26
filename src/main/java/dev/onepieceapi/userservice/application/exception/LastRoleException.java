package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;
import dev.onepieceapi.userservice.domain.RealmRole;

import java.util.UUID;

/**
 * Raised when revoking {@code role} from {@code userId} would leave that user with no
 * roles at all (UF-IDU-15: "at least one valid role must remain assigned").
 */
public class LastRoleException extends ConflictException {

	public LastRoleException(UUID userId, RealmRole role) {
		super(UserErrorCode.LAST_ROLE, message(userId, role));
	}

	private static String message(UUID userId, RealmRole role) {
		return "Cannot remove " + role + " from " + userId + " - at least one role must remain assigned";
	}

}
