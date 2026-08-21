package dev.onepieceapi.userservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsApplicationUserNotFoundToA404ProblemDetail() {
		UUID userId = UUID.randomUUID();

		var ex = new ApplicationUserNotFoundException(userId);
		ProblemDetail problemDetail = this.handler.handleApplicationUserNotFound(ex);

		assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
		assertThat(problemDetail.getDetail()).contains(userId.toString());
	}

}
