package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import dev.onepieceapi.userservice.adapter.in.web.security.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.adapter.in.web.security.SecurityConfig;
import dev.onepieceapi.userservice.application.exception.LastRoleManagerException;
import dev.onepieceapi.userservice.application.exception.PermissionAlreadyExistsException;
import dev.onepieceapi.userservice.application.exception.PermissionInUseException;
import dev.onepieceapi.userservice.application.exception.RoleAlreadyExistsException;
import dev.onepieceapi.userservice.application.exception.RoleInUseException;
import dev.onepieceapi.userservice.application.service.RoleManagementService;
import dev.onepieceapi.userservice.application.service.RoleQueryService;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.PermissionDefinition;
import dev.onepieceapi.userservice.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports the real {@link SecurityConfig}, same reasoning as {@link UserControllerTest} -
 * exercises each endpoint's {@code SecuredEndpoint} permission rule itself.
 */
@WebMvcTest(RoleController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class RoleControllerTest {

	private static final String NO_RELEVANT_PERMISSION = "PERMISSION_docs:read";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RoleQueryService roleQueryService;

	@MockitoBean
	private RoleManagementService roleManagementService;

	@Test
	void aCallerWithRolesReadCanListTheRolePermissionRegistry() throws Exception {
		var rolePermissions = Map.of("ADMIN", List.of("audit:read", "users:read"), "EDITOR",
				List.of("docs:read", "docs:write"));
		when(this.roleQueryService.listRoles()).thenReturn(rolePermissions);

		var request = get("/roles").with(authorities("PERMISSION_roles:read"));
		var response = this.mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();

		assertThat(response.getContentAsString()).contains("\"role\":\"ADMIN\"", "\"role\":\"EDITOR\"",
				"\"audit:read\"", "\"users:read\"", "\"docs:read\"", "\"docs:write\"");
	}

	@Test
	void aCallerWithoutRolesReadCannotListTheRolePermissionRegistry() throws Exception {
		var request = get("/roles").with(authorities(NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithRolesManageCanCreateARole() throws Exception {
		var updated = Map.of("ADMIN", List.of("audit:read"), "NAVIGATOR", List.<String>of());
		when(this.roleManagementService.createRole("NAVIGATOR", null, luffy())).thenReturn(updated);

		var request = post("/roles").with(authorities("PERMISSION_roles:manage"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "NAVIGATOR"}
					""");
		this.mockMvc.perform(request).andExpect(status().isCreated());
	}

	@Test
	void creatingADuplicateRoleReturnsConflict() throws Exception {
		when(this.roleManagementService.createRole("ADMIN", null, luffy()))
			.thenThrow(new RoleAlreadyExistsException("ADMIN"));

		var request = post("/roles").with(authorities("PERMISSION_roles:manage"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "ADMIN"}
					""");
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_ROLE_ALREADY_EXISTS"));
	}

	@Test
	void aCallerWithoutRolesManageCannotCreateARole() throws Exception {
		var request = post("/roles").with(authorities(NO_RELEVANT_PERMISSION))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "NAVIGATOR"}
					""");
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithRolesManageCanDeleteARole() throws Exception {
		var request = delete("/roles/NAVIGATOR").with(authorities("PERMISSION_roles:manage"));
		this.mockMvc.perform(request).andExpect(status().isNoContent());
	}

	@Test
	void deletingAnInUseRoleReturnsConflict() throws Exception {
		var inUse = new RoleInUseException("EDITOR");
		doThrow(inUse).when(this.roleManagementService).deleteRole("EDITOR", luffy());

		var request = delete("/roles/EDITOR").with(authorities("PERMISSION_roles:manage"));
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_ROLE_IN_USE"));
	}

	@Test
	void aCallerWithoutRolesManageCannotDeleteARole() throws Exception {
		var request = delete("/roles/NAVIGATOR").with(authorities(NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithRolesManageCanListEveryPermission() throws Exception {
		var permissions = List.of(new PermissionDefinition("users:read", "List and view crew members"),
				new PermissionDefinition("docs:approve", "Approve documents"));
		when(this.roleQueryService.listPermissions()).thenReturn(permissions);

		var request = get("/permissions").with(authorities("PERMISSION_roles:manage"));
		var response = this.mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();

		String body = response.getContentAsString();
		assertThat(body).contains("\"key\":\"users:read\"", "\"key\":\"docs:approve\"");
	}

	@Test
	void aCallerWithoutRolesManageCannotListPermissions() throws Exception {
		var request = get("/permissions").with(authorities(NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithRolesManageCanCreateAPermission() throws Exception {
		var created = new PermissionDefinition("docs:approve", "Approve documents");
		when(this.roleManagementService.createPermission("docs:approve", "Approve documents", luffy()))
			.thenReturn(created);

		var request = post("/permissions").with(authorities("PERMISSION_roles:manage"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"key": "docs:approve", "description": "Approve documents"}
					""");
		this.mockMvc.perform(request).andExpect(status().isCreated());
	}

	@Test
	void creatingAPermissionWithAMalformedKeyIsRejected() throws Exception {
		var request = post("/permissions").with(authorities("PERMISSION_roles:manage"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"key": "NotValid", "description": "Approve documents"}
					""");
		this.mockMvc.perform(request).andExpect(status().isBadRequest());
	}

	@Test
	void creatingADuplicatePermissionReturnsConflict() throws Exception {
		when(this.roleManagementService.createPermission("users:read", "dup", luffy()))
			.thenThrow(new PermissionAlreadyExistsException("users:read"));

		var request = post("/permissions").with(authorities("PERMISSION_roles:manage"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"key": "users:read", "description": "dup"}
					""");
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_PERMISSION_ALREADY_EXISTS"));
	}

	@Test
	void aCallerWithRolesManageCanDeleteAPermission() throws Exception {
		var request = delete("/permissions/docs:approve").with(authorities("PERMISSION_roles:manage"));
		this.mockMvc.perform(request).andExpect(status().isNoContent());
	}

	@Test
	void deletingAnInUsePermissionReturnsConflict() throws Exception {
		var inUse = new PermissionInUseException("docs:approve");
		doThrow(inUse).when(this.roleManagementService).deletePermission("docs:approve", luffy());

		var request = delete("/permissions/docs:approve").with(authorities("PERMISSION_roles:manage"));
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_PERMISSION_IN_USE"));
	}

	@Test
	void aCallerWithoutRolesManageCannotDeleteAPermission() throws Exception {
		var request = delete("/permissions/docs:approve").with(authorities(NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithoutRolesManageCannotCreateAPermission() throws Exception {
		var request = post("/permissions").with(authorities(NO_RELEVANT_PERMISSION))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"key": "docs:approve", "description": "Approve documents"}
					""");
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithRolesManageCanAssignAPermissionToARole() throws Exception {
		var manage = authorities("PERMISSION_roles:manage");
		var request = put("/roles/EDITOR/permissions/docs:approve").with(manage);
		this.mockMvc.perform(request).andExpect(status().isNoContent());
	}

	@Test
	void aCallerWithoutRolesManageCannotAssignAPermission() throws Exception {
		var request = put("/roles/EDITOR/permissions/docs:approve").with(authorities(NO_RELEVANT_PERMISSION));
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void aCallerWithRolesManageCanRevokeAPermissionFromARole() throws Exception {
		var manage = authorities("PERMISSION_roles:manage");
		var request = delete("/roles/EDITOR/permissions/docs:approve").with(manage);
		this.mockMvc.perform(request).andExpect(status().isNoContent());
	}

	@Test
	void revokingTheLastManagePermissionReturnsConflict() throws Exception {
		doThrow(new LastRoleManagerException("ADMIN")).when(this.roleManagementService)
			.revokePermission("ADMIN", "roles:manage", luffy());

		var manage = authorities("PERMISSION_roles:manage");
		var request = delete("/roles/ADMIN/permissions/roles:manage").with(manage);
		this.mockMvc.perform(request)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("USER_LAST_ROLE_MANAGER"));
	}

	@Test
	void aCallerWithoutRolesManageCannotRevokeAPermission() throws Exception {
		var noAccess = authorities(NO_RELEVANT_PERMISSION);
		var request = delete("/roles/EDITOR/permissions/docs:approve").with(noAccess);
		this.mockMvc.perform(request).andExpect(status().isForbidden());
	}

	private static User luffy() {
		UUID luffyId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
		return new User(luffyId, "luffy", "luffy@onepiece.local", AccountStatus.ACTIVE, List.of("ADMIN"), null);
	}

	private static RequestPostProcessor authorities(String... authorities) {
		var luffy = luffy();
		var jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.build();
		Set<SimpleGrantedAuthority> grantedAuthorities = Set.of(authorities)
			.stream()
			.map(SimpleGrantedAuthority::new)
			.collect(Collectors.toSet());
		return authentication(new ApplicationUserAuthenticationToken(jwt, luffy, grantedAuthorities));
	}

}
