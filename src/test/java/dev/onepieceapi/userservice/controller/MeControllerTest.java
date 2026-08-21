package dev.onepieceapi.userservice.controller;

import dev.onepieceapi.userservice.config.security.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.domain.ApplicationUser;
import dev.onepieceapi.userservice.service.ApplicationUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
class MeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ApplicationUserService applicationUserService;

	@Test
	void returnsEmailAndRolesFromTheTokensRealmRoles() throws Exception {
		var luffy = new ApplicationUser(UUID.randomUUID(), "luffy@onepiece.local");
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.build();
		var admin = new SimpleGrantedAuthority("ROLE_ADMIN");
		var editor = new SimpleGrantedAuthority("ROLE_EDITOR");
		var authorities = Set.of(admin, editor);
		var asLuffy = authentication(new ApplicationUserAuthenticationToken(jwt, luffy, authorities));

		this.mockMvc.perform(get("/me").with(asLuffy)).andExpect(status().isOk()).andExpect(content().json("""
				{"email": "luffy@onepiece.local", "roles": ["ADMIN", "EDITOR"]}
				"""));
	}

}
