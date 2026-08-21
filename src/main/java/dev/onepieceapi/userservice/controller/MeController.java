package dev.onepieceapi.userservice.identity;

import dev.onepieceapi.userservice.config.ApplicationUserAuthenticationToken;
import dev.onepieceapi.userservice.config.SecurityConfig;
import dev.onepieceapi.userservice.user.ApplicationUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.regex.Pattern;

@RestController
class MeController {

	private static final Pattern ROLE_PREFIX_PATTERN = Pattern.compile("^" + SecurityConfig.ROLE_AUTHORITY_PREFIX);

	@GetMapping("/me")
	MeResponse me(ApplicationUserAuthenticationToken authentication) {
		ApplicationUser applicationUser = authentication.getApplicationUser();
		var roles = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter(Objects::nonNull)
			.map(authority -> ROLE_PREFIX_PATTERN.matcher(authority).replaceFirst(""))
			.toList();
		return new MeResponse(applicationUser.email(), applicationUser.status().name(), roles);
	}

}
