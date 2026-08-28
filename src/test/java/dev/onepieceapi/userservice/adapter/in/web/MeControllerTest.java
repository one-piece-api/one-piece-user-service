package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.security.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
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

	@Test
	void returnsUsernameEmailRolesAndPermissionsFromTheAuthenticatedPrincipal() throws Exception {
		var roles = List.of("ADMIN", "EDITOR");
		UUID userId = UUID.randomUUID();
		var luffy = new User(userId, "luffy", "luffy@onepiece.local", AccountStatus.ACTIVE, roles, null);
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.build();
		var admin = new SimpleGrantedAuthority("ROLE_ADMIN");
		var editor = new SimpleGrantedAuthority("ROLE_EDITOR");
		var usersRead = new SimpleGrantedAuthority("PERMISSION_users:read");
		var auditRead = new SimpleGrantedAuthority("PERMISSION_audit:read");
		var authorities = Set.of(admin, editor, usersRead, auditRead);
		var asLuffy = authentication(new ApplicationUserAuthenticationToken(jwt, luffy, authorities));

		this.mockMvc.perform(get("/me").with(asLuffy)).andExpect(status().isOk()).andExpect(content().json("""
				{
					"username": "luffy",
					"email": "luffy@onepiece.local",
					"roles": ["ADMIN", "EDITOR"],
					"permissions": ["users:read", "audit:read"]
				}
				"""));
	}

	@Test
	void returnsAnEmptyPermissionListWhenTheCallerHasNone() throws Exception {
		var roles = List.of("EDITOR");
		UUID userId = UUID.randomUUID();
		var nami = new User(userId, "nami", "nami@onepiece.local", AccountStatus.ACTIVE, roles, null);
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.build();
		var editor = new SimpleGrantedAuthority("ROLE_EDITOR");
		var asNami = authentication(new ApplicationUserAuthenticationToken(jwt, nami, Set.of(editor)));

		this.mockMvc.perform(get("/me").with(asNami)).andExpect(status().isOk()).andExpect(content().json("""
				{
					"username": "nami",
					"email": "nami@onepiece.local",
					"roles": ["EDITOR"],
					"permissions": []
				}
				"""));
	}

}
