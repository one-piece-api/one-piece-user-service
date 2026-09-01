package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.CreatePermissionRequest;
import dev.onepieceapi.userservice.adapter.in.web.dto.CreateRoleRequest;
import dev.onepieceapi.userservice.adapter.in.web.dto.PermissionResponse;
import dev.onepieceapi.userservice.adapter.in.web.dto.RolePermissionsResponse;
import dev.onepieceapi.userservice.application.service.RoleManagementService;
import dev.onepieceapi.userservice.application.service.RoleQueryService;
import dev.onepieceapi.userservice.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The role/permission catalog itself (UF: create/delete a role, create a permission,
 * assign/revoke a permission on a role) - a different resource from a user identity, see
 * {@code RoleDirectoryPort}. Access is enforced per-endpoint by permission authority, not
 * here - see {@code security.SecuredEndpoint}.
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class RoleController {

	private final RoleQueryService roleQueryService;

	private final RoleManagementService roleManagementService;

	/**
	 * The read-only role/permission registry (see
	 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}) - powers the UI's
	 * "Roles &amp; Permissions" panel and lets the frontend compute any user's effective
	 * permissions client-side from their roles, without a per-user server call.
	 */
	@GetMapping(ApiPaths.ROLES)
	List<RolePermissionsResponse> listRoles() {
		return toResponses(this.roleQueryService.listRoles());
	}

	@PostMapping(ApiPaths.ROLES)
	ResponseEntity<List<RolePermissionsResponse>> createRole(@Valid @RequestBody CreateRoleRequest request,
			@AuthenticationPrincipal User user) {
		var updated = this.roleManagementService.createRole(request.name(), request.copyFromRole(), user);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponses(updated));
	}

	@DeleteMapping(ApiPaths.ROLE_BY_NAME)
	ResponseEntity<Void> deleteRole(@PathVariable String role, @AuthenticationPrincipal User user) {
		this.roleManagementService.deleteRole(role, user);
		return ResponseEntity.noContent().build();
	}

	/** Every permission that exists, including one no role currently holds. */
	@GetMapping(ApiPaths.PERMISSIONS)
	List<PermissionResponse> listPermissions() {
		return this.roleQueryService.listPermissions()
			.stream()
			.map(permission -> new PermissionResponse(permission.key(), permission.description()))
			.toList();
	}

	@PostMapping(ApiPaths.PERMISSIONS)
	ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request,
			@AuthenticationPrincipal User user) {
		var created = this.roleManagementService.createPermission(request.key(), request.description(), user);
		var response = new PermissionResponse(created.key(), created.description());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@DeleteMapping(ApiPaths.PERMISSION_BY_KEY)
	ResponseEntity<Void> deletePermission(@PathVariable String permission, @AuthenticationPrincipal User user) {
		this.roleManagementService.deletePermission(permission, user);
		return ResponseEntity.noContent().build();
	}

	@PutMapping(ApiPaths.ROLE_PERMISSION)
	ResponseEntity<Void> assignPermission(@PathVariable String role, @PathVariable String permission,
			@AuthenticationPrincipal User user) {
		this.roleManagementService.assignPermission(role, permission, user);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping(ApiPaths.ROLE_PERMISSION)
	ResponseEntity<Void> revokePermission(@PathVariable String role, @PathVariable String permission,
			@AuthenticationPrincipal User user) {
		this.roleManagementService.revokePermission(role, permission, user);
		return ResponseEntity.noContent().build();
	}

	private static List<RolePermissionsResponse> toResponses(Map<String, List<String>> rolePermissions) {
		return rolePermissions.entrySet()
			.stream()
			.map(entry -> new RolePermissionsResponse(entry.getKey(), entry.getValue()))
			.sorted((a, b) -> a.role().compareTo(b.role()))
			.toList();
	}

}
