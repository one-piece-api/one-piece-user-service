package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.MeResponse;
import dev.onepieceapi.userservice.adapter.in.web.security.SecurityConfig;
import dev.onepieceapi.userservice.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class MeController {

	@GetMapping("/me")
	MeResponse me(@AuthenticationPrincipal User user, Authentication authentication) {
		return new MeResponse(user.username(), user.email(), user.roles(), permissionsOf(authentication));
	}

	/**
	 * Permissions live only as {@code PERMISSION_}-prefixed authorities (see
	 * {@code ApplicationUserJwtAuthenticationConverter}), not on {@link User} - reading
	 * them back here, rather than adding a field meaningful only for "my own identity",
	 * keeps {@link User} the same shape whether it came from a token or an Admin API
	 * lookup of someone else.
	 */
	private static List<String> permissionsOf(Authentication authentication) {
		return authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter(authority -> authority.startsWith(SecurityConfig.PERMISSION_AUTHORITY_PREFIX))
			.map(authority -> authority.substring(SecurityConfig.PERMISSION_AUTHORITY_PREFIX.length()))
			.toList();
	}

}
