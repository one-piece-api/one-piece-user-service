package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.MeResponse;
import dev.onepieceapi.userservice.adapter.in.web.security.Permission;
import dev.onepieceapi.userservice.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

	@GetMapping(ApiPaths.ME)
	MeResponse me(@AuthenticationPrincipal User user, Authentication authentication) {
		var permissions = Permission.allFrom(authentication.getAuthorities());
		return new MeResponse(user.username(), user.email(), user.roles(), permissions);
	}

}
