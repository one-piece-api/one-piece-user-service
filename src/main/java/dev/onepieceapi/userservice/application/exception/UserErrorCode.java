package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.ErrorCode;

/**
 * This service's own error codes (see {@code one-piece-exception}'s {@code ErrorCode} for
 * why each service defines its own rather than sharing one closed registry). Prefixed
 * with {@code USER_} so a client talking to multiple services can tell which one an error
 * code came from.
 */
public enum UserErrorCode implements ErrorCode {

	EMAIL_ALREADY_REGISTERED, NOT_FOUND, INVITATION_NOT_RESENDABLE;

	@Override
	public String code() {
		return "USER_" + name();
	}

}
