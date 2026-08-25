package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ConflictException;

import java.util.UUID;

/**
 * Raised when resending an invitation (UF-IDU-03) targets a user who is not currently
 * {@link dev.onepieceapi.userservice.domain.AccountStatus#INVITATION_EXPIRED} - either
 * the account already has a usable credential or is disabled (no invitation to resend),
 * or it is still PENDING with its current invitation link still valid (resending would
 * only add another concurrently valid link, rather than replacing one that had actually
 * gone stale - see {@code docs/adr/0004-invitation-expiry-gating.md}).
 */
public class InvitationNotResendableException extends ConflictException {

	public InvitationNotResendableException(UUID userId) {
		super(UserErrorCode.INVITATION_NOT_RESENDABLE, "Invitation for " + userId + " is not resendable");
	}

}
