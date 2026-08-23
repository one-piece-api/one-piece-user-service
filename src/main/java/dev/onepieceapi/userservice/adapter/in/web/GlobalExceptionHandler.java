package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates application-level exceptions to HTTP responses, so controllers stay free of
 * status-code concerns. One handler today ({@link EmailAlreadyRegisteredException}, 409);
 * grows as later steps add their own application exceptions.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

}
