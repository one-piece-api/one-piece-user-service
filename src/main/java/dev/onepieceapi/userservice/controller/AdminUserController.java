package dev.onepieceapi.userservice.controller;

import dev.onepieceapi.userservice.controller.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.controller.dto.PageResponse;
import dev.onepieceapi.userservice.service.AdminUserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
		return PageResponse.from(this.adminUserQueryService.list(pageable));
	}

}
