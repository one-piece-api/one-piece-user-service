package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.InviteUserRequest;
import dev.onepieceapi.userservice.adapter.in.web.dto.PageResponse;
import dev.onepieceapi.userservice.adapter.in.web.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.adapter.in.web.mapper.UserSummaryResponseMapper;
import dev.onepieceapi.userservice.application.service.UserAccessService;
import dev.onepieceapi.userservice.application.service.UserInvitationService;
import dev.onepieceapi.userservice.application.service.UserQueryService;
import dev.onepieceapi.userservice.application.service.UserRoleService;
import dev.onepieceapi.userservice.domain.AccountStatus;
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

import java.util.Optional;
import java.util.UUID;

/**
 * The user listing and single-user lookup from UF-IDU-17, the invite endpoint from
 * UF-IDU-01, the resend endpoint from UF-IDU-03, the role assign/revoke endpoints from
 * UF-IDU-15/16, and the revoke-access/reactivate endpoints from UF-IDU-13/14. Access is
 * enforced per-endpoint by permission authority, not here - see
 * {@code security.SecuredEndpoint}. The role/permission catalog itself is
 * {@link RoleController}'s concern, not this one's.
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class UserController {

	private final UserQueryService userQueryService;

	private final UserInvitationService userInvitationService;

	private final UserRoleService userRoleService;

	private final UserAccessService userAccessService;

	/**
	 * {@code q}/{@code role}/{@code status} narrow the listing (Step 15) - any
	 * combination, or none for the original unfiltered page. See
	 * {@code UserDirectoryPort#findUsers} for how a non-empty filter is resolved.
	 */
	@GetMapping(ApiPaths.USERS)
	PageResponse<UserSummaryResponse> listUsers(Pageable pageable, @RequestParam Optional<String> q,
			@RequestParam Optional<String> role, @RequestParam Optional<AccountStatus> status) {
		var filter = new UserFilter(q.orElse(null), role.orElse(null), status.orElse(null));
		Page<UserSummaryResponse> page = this.userQueryService.list(pageable, filter)
			.map(UserSummaryResponseMapper::toResponse);
		return PageResponse.from(page);
	}

	@GetMapping(ApiPaths.USER_BY_ID)
	UserSummaryResponse getUser(@PathVariable UUID userId) {
		return UserSummaryResponseMapper.toResponse(this.userQueryService.getUser(userId));
	}

	@PostMapping(ApiPaths.USERS)
	ResponseEntity<UserSummaryResponse> inviteUser(@Valid @RequestBody InviteUserRequest request,
			@AuthenticationPrincipal User user) {
		var invited = this.userInvitationService.invite(request.email(), request.roles(), user);
		return ResponseEntity.status(HttpStatus.CREATED).body(UserSummaryResponseMapper.toResponse(invited));
	}

	@PostMapping(ApiPaths.USER_RESEND_INVITATION)
	UserSummaryResponse resendInvitation(@PathVariable UUID userId, @AuthenticationPrincipal User user) {
		var target = this.userInvitationService.resend(userId, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

	@PutMapping(ApiPaths.USER_ROLE)
	UserSummaryResponse assignRole(@PathVariable UUID userId, @PathVariable String role,
			@AuthenticationPrincipal User user) {
		var target = this.userRoleService.assignRole(userId, role, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

	@DeleteMapping(ApiPaths.USER_ROLE)
	UserSummaryResponse revokeRole(@PathVariable UUID userId, @PathVariable String role,
			@AuthenticationPrincipal User user) {
		var target = this.userRoleService.revokeRole(userId, role, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

	@PostMapping(ApiPaths.USER_REVOKE_ACCESS)
	UserSummaryResponse revokeAccess(@PathVariable UUID userId, @AuthenticationPrincipal User user) {
		var target = this.userAccessService.revokeAccess(userId, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

	@PostMapping(ApiPaths.USER_REACTIVATE)
	UserSummaryResponse reactivate(@PathVariable UUID userId, @AuthenticationPrincipal User user) {
		var target = this.userAccessService.reactivate(userId, user);
		return UserSummaryResponseMapper.toResponse(target);
	}

}
