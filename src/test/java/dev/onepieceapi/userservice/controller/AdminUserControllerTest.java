package dev.onepieceapi.userservice.controller;

import dev.onepieceapi.userservice.config.security.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.config.security.SecurityConfig;
import dev.onepieceapi.userservice.controller.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.ApplicationUser;
import dev.onepieceapi.userservice.service.AdminUserQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports the real {@link SecurityConfig} (unlike {@code MeControllerTest}) specifically
 * to exercise the "/admin/**" -&gt; hasRole("ADMIN") rule itself, not just controller
 * logic given an already-authenticated principal.
 */
@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AdminUserQueryService adminUserQueryService;

	@Test
	void anAdminCanListUsers() throws Exception {
		var luffy = new ApplicationUser(UUID.randomUUID(), "luffy@onepiece.local");
		List<String> roles = List.of("ADMIN");
		AccountStatus status = AccountStatus.ACTIVE;
		var row = new UserSummaryResponse(luffy.userId(), luffy.email(), status, roles, Instant.EPOCH);
		when(this.adminUserQueryService.list(any())).thenReturn(new PageImpl<>(List.of(row)));

		var request = get("/admin/users").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aNonAdminIsForbidden() throws Exception {
		var nami = new ApplicationUser(UUID.randomUUID(), "nami@onepiece.local");

		var request = get("/admin/users").with(asUser(nami, "EDITOR"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	private static RequestPostProcessor asUser(ApplicationUser user, String... roles) {
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
