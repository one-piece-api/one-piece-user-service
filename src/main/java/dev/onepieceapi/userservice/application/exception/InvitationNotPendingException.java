package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;

import java.util.UUID;

/**
 * Raised when resending an invitation (UF-IDU-03) targets a user who is no longer PENDING
 * (§10) - already activated, or disabled. Resending only ever makes sense for an account
 * that still has no usable credential.
 */
public class InvitationNotPendingException extends ConflictException {

	public InvitationNotPendingException(UUID userId) {
		super(UserErrorCode.INVITATION_NOT_PENDING, "User " + userId + " is not pending activation");
	}

}
