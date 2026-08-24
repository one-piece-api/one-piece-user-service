package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import dev.onepieceapi.userservice.adapter.in.web.security.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.adapter.in.web.security.SecurityConfig;
import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import dev.onepieceapi.userservice.application.service.AdminUserInvitationService;
import dev.onepieceapi.userservice.application.service.AdminUserQueryService;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports the real {@link SecurityConfig} (unlike {@code MeControllerTest}) specifically
 * to exercise the "/admin/**" -&gt; hasRole("ADMIN") rule itself, not just controller
 * logic given an already-authenticated principal. Also imports the shared library's
 * {@link ApplicationExceptionHandler} directly - a {@code @WebMvcTest} slice narrows
 * auto-configuration to what the slice itself needs, so a third-party
 * {@code @RestControllerAdvice} shipped via auto-configuration isn't picked up unless
 * named explicitly (unlike {@code AdminUserListingIntegrationTest}'s full
 * {@code @SpringBootTest} context, which loads it automatically).
 */
@WebMvcTest(AdminUserController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class AdminUserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AdminUserQueryService adminUserQueryService;

	@MockitoBean
	private AdminUserInvitationService adminUserInvitationService;

	@Test
	void anAdminCanListUsers() throws Exception {
		var luffy = luffy();
		List<String> roles = List.of("ADMIN");
		AccountStatus status = AccountStatus.ACTIVE;
		var account = new User(luffy.userId(), luffy.email(), status, roles, Instant.EPOCH);
		when(this.adminUserQueryService.list(any())).thenReturn(new PageImpl<>(List.of(account)));

		var request = get("/admin/users").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aNonAdminIsForbidden() throws Exception {
		var nami = nami();

		var request = get("/admin/users").with(asUser(nami, "EDITOR"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anAdminCanInviteAUser() throws Exception {
		var luffy = luffy();
		List<String> invitedRoles = List.of("EDITOR");
		var invited = new User(UUID.randomUUID(), "usopp@onepiece.local", AccountStatus.PENDING, invitedRoles,
				Instant.EPOCH);
		when(this.adminUserInvitationService.invite("usopp@onepiece.local", Set.of(RealmRole.EDITOR), luffy))
			.thenReturn(invited);

		var request = post("/admin/users").with(asUser(luffy, "ADMIN"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "usopp@onepiece.local", "roles": ["EDITOR"]}
					""");
		this.mockMvc.perform(request).andExpect(status().isCreated());
	}

	@Test
	void invitingWithNoRolesIsRejected() throws Exception {
		var luffy = luffy();

		var request = post("/admin/users").with(asUser(luffy, "ADMIN"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "usopp@onepiece.local", "roles": []}
					""");
		this.mockMvc.perform(request).andExpect(status().isBadRequest());
	}

	@Test
	void invitingAnAlreadyRegisteredEmailReturnsConflict() throws Exception {
		var luffy = luffy();
		when(this.adminUserInvitationService.invite("usopp@onepiece.local", Set.of(RealmRole.EDITOR), luffy))
			.thenThrow(new EmailAlreadyRegisteredException("usopp@onepiece.local"));

		var request = post("/admin/users").with(asUser(luffy, "ADMIN"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "usopp@onepiece.local", "roles": ["EDITOR"]}
					""");
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void aNonAdminCannotInviteAUser() throws Exception {
		var nami = nami();

		var request = post("/admin/users").with(asUser(nami, "EDITOR"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "usopp@onepiece.local", "roles": ["EDITOR"]}
					""");
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	private static User luffy() {
		UUID userId = UUID.randomUUID();
		return new User(userId, "luffy@onepiece.local", AccountStatus.ACTIVE, List.of("ADMIN"), null);
	}

	private static User nami() {
		UUID userId = UUID.randomUUID();
		return new User(userId, "nami@onepiece.local", AccountStatus.ACTIVE, List.of("EDITOR"), null);
	}

	private static RequestPostProcessor asUser(User user, String... roles) {
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.build();
		Set<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
			.map(role -> new SimpleGrantedAuthority(SecurityConfig.ROLE_AUTHORITY_PREFIX + role))
			.collect(Collectors.toSet());
		return authentication(new ApplicationUserAuthenticationToken(jwt, user, authorities));
	}

}
