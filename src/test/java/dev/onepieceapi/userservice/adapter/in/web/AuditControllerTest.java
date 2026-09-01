package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import dev.onepieceapi.userservice.adapter.in.web.security.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.adapter.in.web.security.SecurityConfig;
import dev.onepieceapi.userservice.application.service.AuditQueryService;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.User;
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
 * Imports the real {@link SecurityConfig} specifically to prove {@code GET /audit} is
 * gated by the {@code audit:read} permission authority - see
 * {@code docs/implementation-plan.md}'s Step 17 and {@link UserControllerTest} for the
 * equivalent coverage of the other permission-gated endpoints.
 */
@WebMvcTest(AuditController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class AuditControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuditQueryService auditQueryService;

	@Test
	void aCallerWithTheAuditReadPermissionCanListEvents() throws Exception {
		var event = new AuditEvent(AuditAction.USER_INVITED, UUID.randomUUID(), "luffy@onepiece.local",
				UUID.randomUUID(), "usopp@onepiece.local", null, Instant.EPOCH);
		when(this.auditQueryService.list(any(), any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(event)));

		var request = get("/audit").with(asUserWithAuthorities("PERMISSION_audit:read"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void anAdminRoleAloneWithoutTheAuditReadPermissionIsForbidden() throws Exception {
		var request = get("/audit").with(asUserWithAuthorities("ROLE_ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithNoRelevantAuthorityIsForbidden() throws Exception {
		var request = get("/audit").with(asUserWithAuthorities("PERMISSION_docs:read"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithTheAuditReadPermissionCanListActors() throws Exception {
		when(this.auditQueryService.listActors()).thenReturn(List.of("luffy@onepiece.local"));

		var request = get("/audit/actors").with(asUserWithAuthorities("PERMISSION_audit:read"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithoutTheAuditReadPermissionIsForbiddenFromListingActors() throws Exception {
		var request = get("/audit/actors").with(asUserWithAuthorities("ROLE_ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	private static RequestPostProcessor asUserWithAuthorities(String... authorities) {
		var userId = UUID.randomUUID();
		var user = new User(userId, "luffy", "luffy@onepiece.local", AccountStatus.ACTIVE, List.of(), null);
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.build();
		Set<SimpleGrantedAuthority> grantedAuthorities = Set.of(authorities)
			.stream()
			.map(SimpleGrantedAuthority::new)
			.collect(Collectors.toSet());
		return authentication(new ApplicationUserAuthenticationToken(jwt, user, grantedAuthorities));
	}

}
