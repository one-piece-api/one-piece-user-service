package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import dev.onepieceapi.userservice.adapter.in.web.security.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.adapter.in.web.security.SecurityConfig;
import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import dev.onepieceapi.userservice.application.exception.InvitationNotResendableException;
import dev.onepieceapi.userservice.application.exception.LastAdministratorException;
import dev.onepieceapi.userservice.application.exception.LastRoleException;
import dev.onepieceapi.userservice.application.exception.UserNotFoundException;
import dev.onepieceapi.userservice.application.service.AdminUserAccessService;
import dev.onepieceapi.userservice.application.service.AdminUserInvitationService;
import dev.onepieceapi.userservice.application.service.AdminUserQueryService;
import dev.onepieceapi.userservice.application.service.AdminUserRoleService;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

	@MockitoBean
	private AdminUserRoleService adminUserRoleService;

	@MockitoBean
	private AdminUserAccessService adminUserAccessService;

	@Test
	void anAdminCanListUsers() throws Exception {
		var luffy = luffy();
		List<String> roles = List.of("ADMIN");
		AccountStatus status = AccountStatus.ACTIVE;
		var account = new User(luffy.userId(), luffy.username(), luffy.email(), status, roles, Instant.EPOCH);
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
		UUID invitedId = UUID.randomUUID();
		var invited = new User(invitedId, "usopp@onepiece.local", "usopp@onepiece.local", AccountStatus.PENDING,
				invitedRoles, Instant.EPOCH);
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

	@Test
	void anAdminCanResendAnInvitation() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		var target = new User(targetId, "usopp@onepiece.local", "usopp@onepiece.local", AccountStatus.PENDING,
				List.of("EDITOR"), Instant.EPOCH);
		when(this.adminUserInvitationService.resend(targetId, luffy)).thenReturn(target);

		var request = post("/admin/users/" + targetId + "/resend-invitation").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void resendingForAnUnknownUserReturnsNotFound() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		var notFound = new UserNotFoundException(targetId);
		when(this.adminUserInvitationService.resend(targetId, luffy)).thenThrow(notFound);

		var request = post("/admin/users/" + targetId + "/resend-invitation").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isNotFound());
	}

	@Test
	void resendingWhenNotResendableReturnsConflict() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		when(this.adminUserInvitationService.resend(targetId, luffy))
			.thenThrow(new InvitationNotResendableException(targetId));

		var request = post("/admin/users/" + targetId + "/resend-invitation").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_INVITATION_NOT_RESENDABLE"));
	}

	@Test
	void aNonAdminCannotResendAnInvitation() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = post("/admin/users/" + targetId + "/resend-invitation").with(asUser(nami, "EDITOR"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anAdminCanFetchASingleUser() throws Exception {
		var luffy = luffy();
		when(this.adminUserQueryService.getUser(luffy.userId())).thenReturn(luffy);

		var request = get("/admin/users/" + luffy.userId()).with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void fetchingAnUnknownUserReturnsNotFound() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		when(this.adminUserQueryService.getUser(targetId)).thenThrow(new UserNotFoundException(targetId));

		var request = get("/admin/users/" + targetId).with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isNotFound());
	}

	@Test
	void aNonAdminCannotFetchASingleUser() throws Exception {
		var nami = nami();

		var request = get("/admin/users/" + nami.userId()).with(asUser(nami, "EDITOR"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anAdminCanAssignARole() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		var updated = new User(targetId, "usopp", "usopp@onepiece.local", AccountStatus.ACTIVE,
				List.of("EDITOR", "ADMIN"), Instant.EPOCH);
		when(this.adminUserRoleService.assignRole(targetId, RealmRole.ADMIN, luffy)).thenReturn(updated);

		var request = put("/admin/users/" + targetId + "/roles/ADMIN").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aNonAdminCannotAssignARole() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = put("/admin/users/" + targetId + "/roles/ADMIN").with(asUser(nami, "EDITOR"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anAdminCanRevokeARole() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		List<String> roles = List.of("EDITOR");
		String email = "usopp@onepiece.local";
		var updated = new User(targetId, "usopp", email, AccountStatus.ACTIVE, roles, Instant.EPOCH);
		when(this.adminUserRoleService.revokeRole(targetId, RealmRole.ADMIN, luffy)).thenReturn(updated);

		var request = delete("/admin/users/" + targetId + "/roles/ADMIN").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void revokingTheLastAdministratorRoleReturnsConflict() throws Exception {
		var luffy = luffy();
		when(this.adminUserRoleService.revokeRole(luffy.userId(), RealmRole.ADMIN, luffy))
			.thenThrow(new LastAdministratorException(luffy.userId()));

		var request = delete("/admin/users/" + luffy.userId() + "/roles/ADMIN").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_LAST_ADMINISTRATOR"));
	}

	@Test
	void revokingAUsersLastRoleReturnsConflict() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		when(this.adminUserRoleService.revokeRole(targetId, RealmRole.EDITOR, luffy))
			.thenThrow(new LastRoleException(targetId, RealmRole.EDITOR));

		var request = delete("/admin/users/" + targetId + "/roles/EDITOR").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_LAST_ROLE"));
	}

	@Test
	void aNonAdminCannotRevokeARole() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = delete("/admin/users/" + targetId + "/roles/ADMIN").with(asUser(nami, "EDITOR"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anAdminCanRevokeAccess() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		List<String> roles = List.of("EDITOR");
		String email = "usopp@onepiece.local";
		var updated = new User(targetId, "usopp", email, AccountStatus.DISABLED, roles, Instant.EPOCH);
		when(this.adminUserAccessService.revokeAccess(targetId, luffy)).thenReturn(updated);

		var request = post("/admin/users/" + targetId + "/revoke-access").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void revokingAccessFromTheLastAdministratorReturnsConflict() throws Exception {
		var luffy = luffy();
		when(this.adminUserAccessService.revokeAccess(luffy.userId(), luffy))
			.thenThrow(new LastAdministratorException(luffy.userId()));

		var request = post("/admin/users/" + luffy.userId() + "/revoke-access").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_LAST_ADMINISTRATOR"));
	}

	@Test
	void revokingAccessForAnUnknownUserReturnsNotFound() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		var notFound = new UserNotFoundException(targetId);
		when(this.adminUserAccessService.revokeAccess(targetId, luffy)).thenThrow(notFound);

		var request = post("/admin/users/" + targetId + "/revoke-access").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isNotFound());
	}

	@Test
	void aNonAdminCannotRevokeAccess() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = post("/admin/users/" + targetId + "/revoke-access").with(asUser(nami, "EDITOR"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anAdminCanReactivate() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		List<String> roles = List.of("EDITOR");
		String email = "usopp@onepiece.local";
		var updated = new User(targetId, "usopp", email, AccountStatus.ACTIVE, roles, Instant.EPOCH);
		when(this.adminUserAccessService.reactivate(targetId, luffy)).thenReturn(updated);

		var request = post("/admin/users/" + targetId + "/reactivate").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void reactivatingAnUnknownUserReturnsNotFound() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		var notFound = new UserNotFoundException(targetId);
		when(this.adminUserAccessService.reactivate(targetId, luffy)).thenThrow(notFound);

		var request = post("/admin/users/" + targetId + "/reactivate").with(asUser(luffy, "ADMIN"));
		this.mockMvc.perform(request).andExpect(status().isNotFound());
	}

	@Test
	void aNonAdminCannotReactivate() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = post("/admin/users/" + targetId + "/reactivate").with(asUser(nami, "EDITOR"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void anAdminCanListTheRolePermissionRegistry() throws Exception {
		var luffy = luffy();
		var rolePermissions = Map.of(RealmRole.ADMIN, List.of("audit:read", "users:read"), RealmRole.EDITOR,
				List.of("docs:read", "docs:write"));
		when(this.adminUserQueryService.listRolePermissions()).thenReturn(rolePermissions);

		var request = get("/admin/roles").with(asUser(luffy, "ADMIN"));
		var response = this.mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();

		assertThat(response.getContentAsString()).contains("\"role\":\"ADMIN\"", "\"role\":\"EDITOR\"",
				"\"audit:read\"", "\"users:read\"", "\"docs:read\"", "\"docs:write\"");
	}

	@Test
	void aNonAdminCannotListTheRolePermissionRegistry() throws Exception {
		var nami = nami();

		var request = get("/admin/roles").with(asUser(nami, "EDITOR"));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	private static User luffy() {
		UUID userId = UUID.randomUUID();
		return new User(userId, "luffy", "luffy@onepiece.local", AccountStatus.ACTIVE, List.of("ADMIN"), null);
	}

	private static User nami() {
		UUID userId = UUID.randomUUID();
		return new User(userId, "nami", "nami@onepiece.local", AccountStatus.ACTIVE, List.of("EDITOR"), null);
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
