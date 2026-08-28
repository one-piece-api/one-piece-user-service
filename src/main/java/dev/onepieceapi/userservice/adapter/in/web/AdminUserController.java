package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.InviteUserRequest;
import dev.onepieceapi.userservice.adapter.in.web.dto.PageResponse;
import dev.onepieceapi.userservice.adapter.in.web.dto.RolePermissionsResponse;
import dev.onepieceapi.userservice.adapter.in.web.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.adapter.in.web.mapper.UserSummaryResponseMapper;
import dev.onepieceapi.userservice.application.service.AdminUserAccessService;
import dev.onepieceapi.userservice.application.service.AdminUserInvitationService;
import dev.onepieceapi.userservice.application.service.AdminUserQueryService;
import dev.onepieceapi.userservice.application.service.AdminUserRoleService;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import dev.onepieceapi.userservice.domain.UserFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The user listing and single-user lookup from UF-IDU-17, the invite endpoint from
 * UF-IDU-01, the resend endpoint from UF-IDU-03, the role assign/revoke endpoints from
 * UF-IDU-15/16, and the revoke-access/reactivate endpoints from UF-IDU-13/14. ADMIN-only
 * access is enforced in {@code SecurityConfig} ("/admin/**"), not here.
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class AdminUserController {

	private final AdminUserQueryService adminUserQueryService;

	private final AdminUserInvitationService adminUserInvitationService;

	private final AdminUserRoleService adminUserRoleService;

	private final AdminUserAccessService adminUserAccessService;

	/**
	 * {@code q}/{@code role}/{@code status} narrow the listing (Step 15) - any
	 * combination, or none for the original unfiltered page. See
	 * {@code UserDirectoryPort#findUsers} for how a non-empty filter is resolved.
	 */
	@GetMapping("/admin/users")
	PageResponse<UserSummaryResponse> listUsers(Pageable pageable, @RequestParam Optional<String> q,
			@RequestParam Optional<RealmRole> role, @RequestParam Optional<AccountStatus> status) {
		var filter = new UserFilter(q.orElse(null), role.orElse(null), status.orElse(null));
		Page<UserSummaryResponse> page = this.adminUserQueryService.list(pageable, filter)
			.map(UserSummaryResponseMapper::toResponse);
		return PageResponse.from(page);
	}

	@GetMapping("/admin/users/{userId}")
	UserSummaryResponse getUser(@PathVariable UUID userId) {
		return UserSummaryResponseMapper.toResponse(this.adminUserQueryService.getUser(userId));
	}

	/**
	 * The read-only role/permission registry (see
	 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}) - powers the UI's
	 * "Roles &amp; Permissions" panel and lets the frontend compute any user's effective
	 * permissions client-side from their roles, without a per-user server call.
	 */
	@GetMapping("/admin/roles")
	List<RolePermissionsResponse> listRoles() {
		Map<RealmRole, List<String>> rolePermissions = this.adminUserQueryService.listRolePermissions();
		return rolePermissions.entrySet()
			.stream()
			.map(entry -> new RolePermissionsResponse(entry.getKey().name(), entry.getValue()))
			.sorted((a, b) -> a.role().compareTo(b.role()))
			.toList();
	}

	@PostMapping("/admin/users")
	ResponseEntity<UserSummaryResponse> inviteUser(@Valid @RequestBody InviteUserRequest request,
			@AuthenticationPrincipal User user) {
		var invited = this.adminUserInvitationService.invite(request.email(), request.roles(), user);
		return ResponseEntity.status(HttpStatus.CREATED).body(UserSummaryResponseMapper.toResponse(invited));
	}

	@PostMapping("/admin/users/{userId}/resend-invitation")
	UserSummaryResponse resendInvitation(@PathVariable UUID userId, @AuthenticationPrincipal User user) {
		var target = this.adminUserInvitationService.resend(userId, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

	@PutMapping("/admin/users/{userId}/roles/{role}")
	UserSummaryResponse assignRole(@PathVariable UUID userId, @PathVariable RealmRole role,
			@AuthenticationPrincipal User user) {
		var target = this.adminUserRoleService.assignRole(userId, role, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

	@DeleteMapping("/admin/users/{userId}/roles/{role}")
	UserSummaryResponse revokeRole(@PathVariable UUID userId, @PathVariable RealmRole role,
			@AuthenticationPrincipal User user) {
		var target = this.adminUserRoleService.revokeRole(userId, role, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

	@PostMapping("/admin/users/{userId}/revoke-access")
	UserSummaryResponse revokeAccess(@PathVariable UUID userId, @AuthenticationPrincipal User user) {
		var target = this.adminUserAccessService.revokeAccess(userId, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

	@PostMapping("/admin/users/{userId}/reactivate")
	UserSummaryResponse reactivate(@PathVariable UUID userId, @AuthenticationPrincipal User user) {
		var target = this.adminUserAccessService.reactivate(userId, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

}
