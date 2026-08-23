package dev.onepieceapi.userservice.adapter.in.web.security;

import dev.onepieceapi.userservice.domain.User;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;

/**
 * A validated request's identity, resolved past the raw token to the application's own
 * {@link User} record (see UF-IDU-10) - the authority a controller consults for "who is
 * calling".
 */
public class ApplicationUserAuthenticationToken extends AbstractAuthenticationToken {

	private final Jwt jwt;

	@Getter
	private final User user;

	public ApplicationUserAuthenticationToken(Jwt jwt, User user, Set<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.jwt = jwt;
		this.user = user;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return this.jwt;
	}

	@Override
	public Object getPrincipal() {
		return this.user;
	}

}
