package dev.onepieceapi.userservice.adapter.in.web.security;

import dev.onepieceapi.userservice.domain.ApplicationUser;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A validated request's identity, resolved past the raw token to the application's own
 * {@link ApplicationUser} record (see UF-IDU-10) — the authority a controller consults
 * for "who is calling", alongside the current roles carried by the token itself.
 */
public class ApplicationUserAuthenticationToken extends AbstractAuthenticationToken {

	private static final Pattern ROLE_PREFIX_PATTERN = Pattern.compile("^" + SecurityConfig.ROLE_AUTHORITY_PREFIX);

	private final Jwt jwt;

	@Getter
	private final ApplicationUser applicationUser;

	public ApplicationUserAuthenticationToken(Jwt jwt, ApplicationUser applicationUser,
			Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.jwt = jwt;
		this.applicationUser = applicationUser;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return this.jwt;
	}

	@Override
	public Object getPrincipal() {
		return this.applicationUser;
	}

	public List<String> getRoles() {
		return getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.filter(Objects::nonNull)
			.map(authority -> ROLE_PREFIX_PATTERN.matcher(authority).replaceFirst(""))
			.toList();
	}

}
