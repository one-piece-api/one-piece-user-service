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
import dev.onepieceapi.userservice.domain.UserFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * to exercise each endpoint's {@code SecuredEndpoint} permission rule itself, not just
 * controller logic given an already-authenticated principal. A caller lacking the
 * required permission is granted {@code PERMISSION_docs:read} (a real, unrelated EDITOR
 * permission) rather than no authorities at all, so these cases prove "wrong permission",
 * not just "no permission" - matching {@link AdminAuditControllerTest}'s
 * {@code aCallerWithNoRelevantAuthorityIsForbidden} style. Also imports the shared
 * library's {@link ApplicationExceptionHandler} directly - a {@code @WebMvcTest} slice
 * narrows auto-configuration to what the slice itself needs, so a third-party
 * {@code @RestControllerAdvice} shipped via auto-configuration isn't picked up unless
 * named explicitly (unlike {@code AdminUserListingIntegrationTest}'s full
 * {@code @SpringBootTest} context, which loads it automatically).
 */
@WebMvcTest(AdminUserController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class AdminUserControllerTest {

	private static final String NO_RELEVANT_PERMISSION = "PERMISSION_docs:read";

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
	void aCallerWithUsersReadCanListUsers() throws Exception {
		var luffy = luffy();
		List<String> roles = List.of("ADMIN");
		AccountStatus status = AccountStatus.ACTIVE;
		var account = new User(luffy.userId(), luffy.username(), luffy.email(), status, roles, Instant.EPOCH);
		when(this.adminUserQueryService.list(any(), any())).thenReturn(new PageImpl<>(List.of(account)));

		var request = get("/users").with(asUserWithAuthorities(luffy, "PERMISSION_users:read"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithUsersReadCanFilterUsers() throws Exception {
		var luffy = luffy();
		List<String> roles = List.of("ADMIN");
		var account = new User(luffy.userId(), luffy.username(), luffy.email(), AccountStatus.ACTIVE, roles,
				Instant.EPOCH);
		var filterCaptor = ArgumentCaptor.forClass(UserFilter.class);
		when(this.adminUserQueryService.list(any(), filterCaptor.capture()))
			.thenReturn(new PageImpl<>(List.of(account)));

		var request = get("/users?q=luf&role=ADMIN&status=ACTIVE")
			.with(asUserWithAuthorities(luffy, "PERMISSION_users:read"));
		this.mockMvc.perform(request).andExpect(status().isOk());

		var expectedFilter = new UserFilter("luf", RealmRole.ADMIN, AccountStatus.ACTIVE);
		assertThat(filterCaptor.getValue()).isEqualTo(expectedFilter);
	}

	@Test
	void aCallerWithoutUsersReadIsForbiddenToListUsers() throws Exception {
		var nami = nami();

		var request = get("/users").with(asUserWithAuthorities(nami, NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithUsersInviteCanInviteAUser() throws Exception {
		var luffy = luffy();
		List<String> invitedRoles = List.of("EDITOR");
		UUID invitedId = UUID.randomUUID();
		var invited = new User(invitedId, "usopp@onepiece.local", "usopp@onepiece.local", AccountStatus.PENDING,
				invitedRoles, Instant.EPOCH);
		when(this.adminUserInvitationService.invite("usopp@onepiece.local", Set.of(RealmRole.EDITOR), luffy))
			.thenReturn(invited);

		var request = post("/users").with(asUserWithAuthorities(luffy, "PERMISSION_users:invite"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "usopp@onepiece.local", "roles": ["EDITOR"]}
					""");
		this.mockMvc.perform(request).andExpect(status().isCreated());
	}

	@Test
	void invitingWithNoRolesIsRejected() throws Exception {
		var luffy = luffy();

		var request = post("/users").with(asUserWithAuthorities(luffy, "PERMISSION_users:invite"))
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

		var request = post("/users").with(asUserWithAuthorities(luffy, "PERMISSION_users:invite"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "usopp@onepiece.local", "roles": ["EDITOR"]}
					""");
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void aCallerWithoutUsersInviteCannotInviteAUser() throws Exception {
		var nami = nami();

		var request = post("/users").with(asUserWithAuthorities(nami, NO_RELEVANT_PERMISSION))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "usopp@onepiece.local", "roles": ["EDITOR"]}
					""");
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithUsersInviteCanResendAnInvitation() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		var target = new User(targetId, "usopp@onepiece.local", "usopp@onepiece.local", AccountStatus.PENDING,
				List.of("EDITOR"), Instant.EPOCH);
		when(this.adminUserInvitationService.resend(targetId, luffy)).thenReturn(target);

		var request = post("/users/" + targetId + "/resend-invitation")
			.with(asUserWithAuthorities(luffy, "PERMISSION_users:invite"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void resendingForAnUnknownUserReturnsNotFound() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		var notFound = new UserNotFoundException(targetId);
		when(this.adminUserInvitationService.resend(targetId, luffy)).thenThrow(notFound);

		var request = post("/users/" + targetId + "/resend-invitation")
			.with(asUserWithAuthorities(luffy, "PERMISSION_users:invite"));
		this.mockMvc.perform(request).andExpect(status().isNotFound());
	}

	@Test
	void resendingWhenNotResendableReturnsConflict() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		when(this.adminUserInvitationService.resend(targetId, luffy))
			.thenThrow(new InvitationNotResendableException(targetId));

		var request = post("/users/" + targetId + "/resend-invitation")
			.with(asUserWithAuthorities(luffy, "PERMISSION_users:invite"));
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_INVITATION_NOT_RESENDABLE"));
	}

	@Test
	void aCallerWithoutUsersInviteCannotResendAnInvitation() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = post("/users/" + targetId + "/resend-invitation")
			.with(asUserWithAuthorities(nami, NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithUsersReadCanFetchASingleUser() throws Exception {
		var luffy = luffy();
		when(this.adminUserQueryService.getUser(luffy.userId())).thenReturn(luffy);

		var authority = asUserWithAuthorities(luffy, "PERMISSION_users:read");
		var request = get("/users/" + luffy.userId()).with(authority);
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void fetchingAnUnknownUserReturnsNotFound() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		when(this.adminUserQueryService.getUser(targetId)).thenThrow(new UserNotFoundException(targetId));

		var request = get("/users/" + targetId).with(asUserWithAuthorities(luffy, "PERMISSION_users:read"));
		this.mockMvc.perform(request).andExpect(status().isNotFound());
	}

	@Test
	void aCallerWithoutUsersReadCannotFetchASingleUser() throws Exception {
		var nami = nami();

		var request = get("/users/" + nami.userId()).with(asUserWithAuthorities(nami, NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithRolesWriteCanAssignARole() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		var updated = new User(targetId, "usopp", "usopp@onepiece.local", AccountStatus.ACTIVE,
				List.of("EDITOR", "ADMIN"), Instant.EPOCH);
		when(this.adminUserRoleService.assignRole(targetId, RealmRole.ADMIN, luffy)).thenReturn(updated);

		var request = put("/users/" + targetId + "/roles/ADMIN")
			.with(asUserWithAuthorities(luffy, "PERMISSION_roles:write"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void aCallerWithoutRolesWriteCannotAssignARole() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = put("/users/" + targetId + "/roles/ADMIN")
			.with(asUserWithAuthorities(nami, NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithRolesWriteCanRevokeARole() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		List<String> roles = List.of("EDITOR");
		String email = "usopp@onepiece.local";
		var updated = new User(targetId, "usopp", email, AccountStatus.ACTIVE, roles, Instant.EPOCH);
		when(this.adminUserRoleService.revokeRole(targetId, RealmRole.ADMIN, luffy)).thenReturn(updated);

		var request = delete("/users/" + targetId + "/roles/ADMIN")
			.with(asUserWithAuthorities(luffy, "PERMISSION_roles:write"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void revokingTheLastAdministratorRoleReturnsConflict() throws Exception {
		var luffy = luffy();
		when(this.adminUserRoleService.revokeRole(luffy.userId(), RealmRole.ADMIN, luffy))
			.thenThrow(new LastAdministratorException(luffy.userId()));

		var request = delete("/users/" + luffy.userId() + "/roles/ADMIN")
			.with(asUserWithAuthorities(luffy, "PERMISSION_roles:write"));
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

		var request = delete("/users/" + targetId + "/roles/EDITOR")
			.with(asUserWithAuthorities(luffy, "PERMISSION_roles:write"));
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_LAST_ROLE"));
	}

	@Test
	void aCallerWithoutRolesWriteCannotRevokeARole() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = delete("/users/" + targetId + "/roles/ADMIN")
			.with(asUserWithAuthorities(nami, NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithAccessWriteCanRevokeAccess() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		List<String> roles = List.of("EDITOR");
		String email = "usopp@onepiece.local";
		var updated = new User(targetId, "usopp", email, AccountStatus.DISABLED, roles, Instant.EPOCH);
		when(this.adminUserAccessService.revokeAccess(targetId, luffy)).thenReturn(updated);

		var request = post("/users/" + targetId + "/revoke-access")
			.with(asUserWithAuthorities(luffy, "PERMISSION_access:write"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void revokingAccessFromTheLastAdministratorReturnsConflict() throws Exception {
		var luffy = luffy();
		when(this.adminUserAccessService.revokeAccess(luffy.userId(), luffy))
			.thenThrow(new LastAdministratorException(luffy.userId()));

		var request = post("/users/" + luffy.userId() + "/revoke-access")
			.with(asUserWithAuthorities(luffy, "PERMISSION_access:write"));
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

		var request = post("/users/" + targetId + "/revoke-access")
			.with(asUserWithAuthorities(luffy, "PERMISSION_access:write"));
		this.mockMvc.perform(request).andExpect(status().isNotFound());
	}

	@Test
	void aCallerWithoutAccessWriteCannotRevokeAccess() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = post("/users/" + targetId + "/revoke-access")
			.with(asUserWithAuthorities(nami, NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithAccessWriteCanReactivate() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		List<String> roles = List.of("EDITOR");
		String email = "usopp@onepiece.local";
		var updated = new User(targetId, "usopp", email, AccountStatus.ACTIVE, roles, Instant.EPOCH);
		when(this.adminUserAccessService.reactivate(targetId, luffy)).thenReturn(updated);

		var request = post("/users/" + targetId + "/reactivate")
			.with(asUserWithAuthorities(luffy, "PERMISSION_access:write"));
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void reactivatingAnUnknownUserReturnsNotFound() throws Exception {
		var luffy = luffy();
		var targetId = UUID.randomUUID();
		var notFound = new UserNotFoundException(targetId);
		when(this.adminUserAccessService.reactivate(targetId, luffy)).thenThrow(notFound);

		var request = post("/users/" + targetId + "/reactivate")
			.with(asUserWithAuthorities(luffy, "PERMISSION_access:write"));
		this.mockMvc.perform(request).andExpect(status().isNotFound());
	}

	@Test
	void aCallerWithoutAccessWriteCannotReactivate() throws Exception {
		var nami = nami();
		var targetId = UUID.randomUUID();

		var request = post("/users/" + targetId + "/reactivate")
			.with(asUserWithAuthorities(nami, NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithUsersReadCanListTheRolePermissionRegistry() throws Exception {
		var luffy = luffy();
		var rolePermissions = Map.of(RealmRole.ADMIN, List.of("audit:read", "users:read"), RealmRole.EDITOR,
				List.of("docs:read", "docs:write"));
		when(this.adminUserQueryService.listRolePermissions()).thenReturn(rolePermissions);

		var request = get("/roles").with(asUserWithAuthorities(luffy, "PERMISSION_users:read"));
		var response = this.mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();

		assertThat(response.getContentAsString()).contains("\"role\":\"ADMIN\"", "\"role\":\"EDITOR\"",
				"\"audit:read\"", "\"users:read\"", "\"docs:read\"", "\"docs:write\"");
	}

	@Test
	void aCallerWithoutUsersReadCannotListTheRolePermissionRegistry() throws Exception {
		var nami = nami();

		var request = get("/roles").with(asUserWithAuthorities(nami, NO_RELEVANT_PERMISSION));
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

	private static RequestPostProcessor asUserWithAuthorities(User user, String... authorities) {
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
