package dev.onepieceapi.userservice.controller;

import dev.onepieceapi.userservice.config.security.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.controller.dto.MeResponse;
import dev.onepieceapi.userservice.domain.ApplicationUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

	@GetMapping("/me")
	MeResponse me(ApplicationUserAuthenticationToken authentication) {
		ApplicationUser applicationUser = authentication.getApplicationUser();
		return new MeResponse(applicationUser.email(), applicationUser.statusName(), authentication.getRoles());
	}

}
