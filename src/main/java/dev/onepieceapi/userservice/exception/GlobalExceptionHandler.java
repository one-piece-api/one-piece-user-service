package dev.onepieceapi.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain/service exceptions into RFC 9457 {@link ProblemDetail} responses for
 * requests handled by Spring MVC. Authentication-time failures (an unknown or disabled
 * user resolved from a JWT) never reach here: they are translated to
 * {@code InvalidBearerTokenException} inside
 * {@code ApplicationUserJwtAuthenticationConverter} and resolved by Spring Security's own
 * entry point, ahead of the dispatcher servlet.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(ApplicationUserNotFoundException.class)
	ProblemDetail handleApplicationUserNotFound(ApplicationUserNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

}
