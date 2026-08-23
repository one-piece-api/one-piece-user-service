package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.MeResponse;
import dev.onepieceapi.userservice.domain.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

	@GetMapping("/me")
	MeResponse me(@AuthenticationPrincipal User user) {
		return new MeResponse(user.email(), user.roles());
	}

}
