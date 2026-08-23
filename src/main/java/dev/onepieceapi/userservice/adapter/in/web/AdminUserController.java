package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.InviteUserRequest;
import dev.onepieceapi.userservice.adapter.in.web.dto.PageResponse;
import dev.onepieceapi.userservice.adapter.in.web.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.adapter.in.web.mapper.UserSummaryResponseMapper;
import dev.onepieceapi.userservice.application.service.AdminUserInvitationService;
import dev.onepieceapi.userservice.application.service.AdminUserQueryService;
import dev.onepieceapi.userservice.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The user listing from UF-IDU-17 and the invite endpoint from UF-IDU-01. ADMIN-only
 * access is enforced in {@code SecurityConfig} ("/admin/**"), not here.
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class AdminUserController {

	private final AdminUserQueryService adminUserQueryService;

	private final AdminUserInvitationService adminUserInvitationService;

	@GetMapping("/admin/users")
	PageResponse<UserSummaryResponse> listUsers(Pageable pageable) {
		Page<UserSummaryResponse> page = this.adminUserQueryService.list(pageable)
			.map(UserSummaryResponseMapper::toResponse);
		return PageResponse.from(page);
	}

	@PostMapping("/admin/users")
	ResponseEntity<UserSummaryResponse> inviteUser(@Valid @RequestBody InviteUserRequest request,
			@AuthenticationPrincipal User user) {
		var invited = this.adminUserInvitationService.invite(request.email(), request.roles(), user);
		return ResponseEntity.status(HttpStatus.CREATED).body(UserSummaryResponseMapper.toResponse(invited));
	}

}
