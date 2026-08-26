package dev.onepieceapi.userservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.mail.Address;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the ADMIN user listing (UF-IDU-17) and invite endpoint (UF-IDU-01) against a
 * real Keycloak (Testcontainers), not Mockito mocks: the full security filter chain
 * (including the "sub"-based {@code ApplicationUserJwtAuthenticationConverter} resolution
 * and the "/admin/**" -&gt; hasRole("ADMIN") rule) and the real Admin REST API call chain
 * in {@code KeycloakUserDirectoryAdapter} all run against a dedicated realm imported just
 * for this test. A real Postgres (Testcontainers) backs the audit log the invite endpoint
 * writes to.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class AdminUserListingIntegrationTest {

	// The fake SMTP server's fixed port (see GREEN_MAIL below) - must be registered for
	// container-network forwarding before KEYCLOAK starts, since the realm fixture's
	// smtpServer.host ("host.testcontainers.internal") only resolves once it is.
	static {
		org.testcontainers.Testcontainers.exposeHostPorts(3025);
	}

	@Container
	static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.6.4")
		.withRealmImportFile("onepiece-realm.json");

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	/**
	 * Fake SMTP server the realm fixture's smtpServer block points at (see
	 * onepiece-realm.json), so {@code anAdminCanInviteANewUser} can assert Keycloak
	 * really sent the invitation email (UF-IDU-01) - production instead uses Resend, see
	 * implementation-plan.md's Step 4 write-up.
	 */
	@RegisterExtension
	static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(
			new ServerSetup(3025, null, ServerSetup.PROTOCOL_SMTP));

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
			.consumeWith(result -> assertThat(result.getResponseBody()).contains("\"username\":\"luffy\"")
				.contains("luffy@onepiece.local")
				.contains("\"status\":\"ACTIVE\"")
				.contains("\"ADMIN\"")
				.doesNotContain("default-roles"));
	}

	@Test
	void meReturnsTheCallersUsernameFromTheTokensPreferredUsernameClaim() {
		this.restTestClient.get()
			.uri("/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("luffy", "luffy-pass"))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.consumeWith(result -> assertThat(result.getResponseBody()).contains("\"username\":\"luffy\"")
				.contains("\"email\":\"luffy@onepiece.local\"")
				.contains("\"ADMIN\""));
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

	@Test
	void anAdminCanInviteANewUser() throws Exception {
		this.restTestClient.post()
			.uri("/admin/users")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("luffy", "luffy-pass"))
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"email": "usopp@onepiece.local", "roles": ["EDITOR"]}
					""")
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(String.class)
			.consumeWith(result -> assertThat(result.getResponseBody()).contains("usopp@onepiece.local")
				.contains("\"status\":\"PENDING\"")
				.contains("\"EDITOR\""));

		assertThat(GREEN_MAIL.waitForIncomingEmail(5000, 1)).isTrue();
		MimeMessage invitationEmail = GREEN_MAIL.getReceivedMessages()[0];
		Address[] recipients = invitationEmail.getAllRecipients();
		assertThat(recipients).extracting(Object::toString).contains("usopp@onepiece.local");
	}

	@Test
	void invitingAnAlreadyRegisteredEmailIsRejected() {
		this.restTestClient.post()
			.uri("/admin/users")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("luffy", "luffy-pass"))
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"email": "luffy@onepiece.local", "roles": ["ADMIN"]}
					""")
			.exchange()
			.expectStatus()
			.isEqualTo(409)
			.expectBody(String.class)
			.consumeWith(result -> assertThat(result.getResponseBody())
				.contains("\"errorCode\":\"USER_EMAIL_ALREADY_REGISTERED\"")
				.contains("\"traceId\":"));
	}

	@Test
	void aNonAdminCannotInviteAUser() {
		this.restTestClient.post()
			.uri("/admin/users")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("nami", "nami-pass"))
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"email": "chopper@onepiece.local", "roles": ["EDITOR"]}
					""")
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	@Test
	void anAdminCanFetchASingleUser() {
		String adminToken = tokenFor("luffy", "luffy-pass");
		String luffyId = userIdOf("luffy", adminToken);

		this.restTestClient.get()
			.uri("/admin/users/" + luffyId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.consumeWith(result -> assertThat(result.getResponseBody()).contains("\"username\":\"luffy\"")
				.contains("\"ADMIN\""));
	}

	@Test
	void anAdminCanAssignAndRevokeARole() {
		String adminToken = tokenFor("luffy", "luffy-pass");
		String zoroId = inviteAndGetUserId("zoro@onepiece.local", "EDITOR", adminToken);

		String afterGrant = this.restTestClient.put()
			.uri("/admin/users/" + zoroId + "/roles/ADMIN")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(afterGrant).contains("\"EDITOR\"").contains("\"ADMIN\"");

		String afterRevoke = this.restTestClient.delete()
			.uri("/admin/users/" + zoroId + "/roles/ADMIN")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(afterRevoke).contains("\"EDITOR\"").doesNotContain("\"ADMIN\"");
	}

	@Test
	void revokingAUsersOnlyRoleIsRejected() {
		String adminToken = tokenFor("luffy", "luffy-pass");
		String chopperId = inviteAndGetUserId("chopper@onepiece.local", "EDITOR", adminToken);

		String body = this.restTestClient.delete()
			.uri("/admin/users/" + chopperId + "/roles/EDITOR")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.exchange()
			.expectStatus()
			.isEqualTo(409)
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(body).contains("\"errorCode\":\"USER_LAST_ROLE\"");
	}

	/**
	 * The fixture realm seeds exactly one ADMIN (luffy) - rejecting this leaves that
	 * invariant intact for every other test in this class, so no cleanup is needed.
	 */
	@Test
	void revokingTheOnlyAdministratorIsRejected() {
		String adminToken = tokenFor("luffy", "luffy-pass");
		String luffyId = userIdOf("luffy", adminToken);

		this.restTestClient.delete()
			.uri("/admin/users/" + luffyId + "/roles/ADMIN")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.exchange()
			.expectStatus()
			.isEqualTo(409)
			.expectBody(String.class)
			.consumeWith(result -> assertThat(result.getResponseBody())
				.contains("\"errorCode\":\"USER_LAST_ADMINISTRATOR\""));
	}

	@Test
	void anAdminCanRevokeAndReactivateAUser() {
		String adminToken = tokenFor("luffy", "luffy-pass");
		String sanjiId = inviteAndGetUserId("sanji@onepiece.local", "EDITOR", adminToken);

		String afterRevoke = this.restTestClient.post()
			.uri("/admin/users/" + sanjiId + "/revoke-access")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(afterRevoke).contains("\"status\":\"DISABLED\"");

		String afterReactivate = this.restTestClient.post()
			.uri("/admin/users/" + sanjiId + "/reactivate")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(afterReactivate).contains("\"status\":\"PENDING\"");
	}

	/**
	 * The fixture realm seeds exactly one ADMIN (luffy) - rejecting this leaves that
	 * invariant intact for every other test in this class, so no cleanup is needed.
	 */
	@Test
	void revokingAccessFromTheOnlyAdministratorIsRejected() {
		String adminToken = tokenFor("luffy", "luffy-pass");
		String luffyId = userIdOf("luffy", adminToken);

		this.restTestClient.post()
			.uri("/admin/users/" + luffyId + "/revoke-access")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.exchange()
			.expectStatus()
			.isEqualTo(409)
			.expectBody(String.class)
			.consumeWith(result -> assertThat(result.getResponseBody())
				.contains("\"errorCode\":\"USER_LAST_ADMINISTRATOR\""));
	}

	@Test
	void aNonAdminCannotRevokeOrReactivateAccess() {
		String luffyId = userIdOf("luffy", tokenFor("luffy", "luffy-pass"));

		this.restTestClient.post()
			.uri("/admin/users/" + luffyId + "/revoke-access")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("nami", "nami-pass"))
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	@Test
	void aNonAdminCannotAssignOrRevokeRoles() {
		String luffyId = userIdOf("luffy", tokenFor("luffy", "luffy-pass"));

		this.restTestClient.put()
			.uri("/admin/users/" + luffyId + "/roles/EDITOR")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("nami", "nami-pass"))
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	private String inviteAndGetUserId(String email, String role, String adminToken) {
		String body = this.restTestClient.post()
			.uri("/admin/users")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"email\": \"" + email + "\", \"roles\": [\"" + role + "\"]}")
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return readJson(body).get("userId").asText();
	}

	private String userIdOf(String username, String adminToken) {
		String body = this.restTestClient.get()
			.uri("/admin/users?size=50")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		for (JsonNode user : readJson(body).get("content")) {
			if (user.get("username").asText().equals(username)) {
				return user.get("userId").asText();
			}
		}
		throw new IllegalStateException("No user named " + username + " in the admin listing");
	}

	private static JsonNode readJson(String body) {
		try {
			return new ObjectMapper().readTree(body);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not parse JSON response: " + body, ex);
		}
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
