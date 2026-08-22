package dev.onepieceapi.userservice;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the ADMIN user listing (UF-IDU-17) against a real Keycloak (Testcontainers),
 * not Mockito mocks: the full security filter chain (including the "sub"-based
 * {@code ApplicationUserJwtAuthenticationConverter} resolution and the "/admin/**" ->
 * hasRole("ADMIN") rule) and the real Admin REST API call chain in {@code KeycloakClient}
 * all run against a dedicated realm imported just for this test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class AdminUserListingIntegrationTest {

	@Container
	static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.6.4")
		.withRealmImportFile("onepiece-realm.json");

	private static final String ADMIN_CLIENT_SECRET = "test-admin-client-secret";

	@Autowired
	private RestTestClient restTestClient;

	@DynamicPropertySource
	static void keycloakProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
				AdminUserListingIntegrationTest::issuerUri);
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
				() -> issuerUri() + "/protocol/openid-connect/certs");
		registry.add("keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
		registry.add("keycloak.admin.client-secret", () -> ADMIN_CLIENT_SECRET);
	}

	@Test
	void anAdminSeesTheRealCrewWithoutTheAutoAssignedDefaultRole() {
		this.restTestClient.get()
			.uri("/admin/users")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("luffy", "luffy-pass"))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.consumeWith(result -> assertThat(result.getResponseBody()).contains("luffy@onepiece.local")
				.contains("\"status\":\"ACTIVE\"")
				.contains("\"ADMIN\"")
				.doesNotContain("default-roles"));
	}

	@Test
	void aNonAdminIsForbidden() {
		this.restTestClient.get()
			.uri("/admin/users")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("nami", "nami-pass"))
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	private static String tokenFor(String username, String password) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "password");
		form.add("client_id", "test-client");
		form.add("username", username);
		form.add("password", password);

		Map<String, Object> token = RestClient.create()
			.post()
			.uri(issuerUri() + "/protocol/openid-connect/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(new ParameterizedTypeReference<Map<String, Object>>() {
			});

		return (String) token.get("access_token");
	}

	private static String issuerUri() {
		return KEYCLOAK.getAuthServerUrl() + "/realms/onepiece";
	}

}
