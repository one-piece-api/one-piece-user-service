package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.PageResponse;
import dev.onepieceapi.userservice.adapter.in.web.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.adapter.in.web.mapper.UserSummaryResponseMapper;
import dev.onepieceapi.userservice.application.service.AdminUserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The user listing from UF-IDU-17. ADMIN-only access is enforced in
 * {@code SecurityConfig} ("/admin/**"), not here.
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class AdminUserController {

	private final AdminUserQueryService adminUserQueryService;

	@GetMapping("/admin/users")
	PageResponse<UserSummaryResponse> listUsers(Pageable pageable) {
		Page<UserSummaryResponse> page = this.adminUserQueryService.list(pageable)
			.map(UserSummaryResponseMapper::toResponse);
		return PageResponse.from(page);
	}

}
