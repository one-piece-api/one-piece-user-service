package dev.onepieceapi.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Snapshot test for the committed OpenAPI contract: the spec generated from the running
 * application's controllers must match {@code openapi/openapi.yaml} exactly, so an API
 * change can't reach main without its contract (and the Bruno collection built from it)
 * being regenerated and reviewed alongside. {@code ./gradlew updateOpenApiSpec} rewrites
 * the file instead of asserting. Also covers who may read the served spec and its Swagger
 * UI. See {@code docs/adr/0014-openapi-contract-and-bruno-collection.md}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiSpecTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Path SPEC = Path.of("openapi", "openapi.yaml");

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser
	void committedSpecMatchesTheControllers() throws Exception {
		String generated = this.mockMvc.perform(get("/v3/api-docs.yaml"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		if (Boolean.getBoolean("openapi.update")) {
			Files.createDirectories(SPEC.getParent());
			Files.writeString(SPEC, generated);
			return;
		}
		assertThat(Files.exists(SPEC)).as("%s is missing - run ./gradlew updateOpenApiSpec", SPEC).isTrue();
		assertThat(normalized(generated)).as("%s is stale - run ./gradlew updateOpenApiSpec", SPEC)
			.isEqualTo(normalized(Files.readString(SPEC)));
	}

	@Test
	void theSpecAndSwaggerUiRequireSignIn() throws Exception {
		this.mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	void swaggerUiIsServedToASignedInUser() throws Exception {
		this.mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	/** Ignores line-ending differences introduced by a Windows checkout. */
	private static String normalized(String yaml) {
		return yaml.replace("\r\n", "\n");
	}

}
