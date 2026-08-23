package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.MeResponse;
import dev.onepieceapi.userservice.adapter.in.web.security.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.domain.ApplicationUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

	@GetMapping("/me")
	MeResponse me(ApplicationUserAuthenticationToken authentication) {
		ApplicationUser applicationUser = authentication.getApplicationUser();
		return new MeResponse(applicationUser.email(), authentication.getRoles());
	}

}
