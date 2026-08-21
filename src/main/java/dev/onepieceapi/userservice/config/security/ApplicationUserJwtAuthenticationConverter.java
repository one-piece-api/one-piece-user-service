package dev.onepieceapi.userservice.config.security;

import dev.onepieceapi.userservice.domain.ApplicationUser;
import dev.onepieceapi.userservice.exception.ApplicationUserNotFoundException;
import dev.onepieceapi.userservice.service.ApplicationUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves a validated JWT to the corresponding {@link ApplicationUser} record and
 * rejects the request if the token does not resolve to exactly one known application user
 * (UF-IDU-10). Account status is not checked here: Keycloak is the sole owner of it (see
 * {@link ApplicationUser}), so a revocation (UF-IDU-13) takes effect at the identity
 * provider immediately but only reaches an already-issued access token once that token's
 * own short lifetime expires and refresh fails: an accepted, bounded window, not a local
 * per-request check.
 * <p>
 * Roles are read from the token's own {@code realm_access.roles} claim: Keycloak
 * recomputes that claim on every token issuance, including the silent refresh
 * oauth2-proxy already performs, so a role change (UF-IDU-15) reaches authorization on
 * the next refresh without needing a local mirror or forcing the user to log out.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class ApplicationUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final String USER_ID_CLAIM = "userId";

	private static final String REALM_ACCESS_CLAIM = "realm_access";

	private static final String REALM_ROLES_CLAIM = "roles";

	private final ApplicationUserService applicationUserService;

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UUID userId = resolveUserId(jwt);
		ApplicationUser applicationUser = resolveApplicationUser(userId);
		return new ApplicationUserAuthenticationToken(jwt, applicationUser, realmRoleAuthorities(jwt));
	}

	private ApplicationUser resolveApplicationUser(UUID userId) {
		try {
			return this.applicationUserService.findByUserId(userId);
		}
		catch (ApplicationUserNotFoundException ex) {
			throw new InvalidBearerTokenException("Token does not resolve to a known user", ex);
		}
	}

	private static UUID resolveUserId(Jwt jwt) {
		String claim = jwt.getClaimAsString(USER_ID_CLAIM);
		if (claim == null) {
			throw new InvalidBearerTokenException("Token is missing the userId claim");
		}
		try {
			return UUID.fromString(claim);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidBearerTokenException("Token userId claim is not a valid identifier");
		}
	}

	private static List<SimpleGrantedAuthority> realmRoleAuthorities(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
		if (realmAccess == null || !(realmAccess.get(REALM_ROLES_CLAIM) instanceof List<?> roles)) {
			return List.of();
		}
		List<SimpleGrantedAuthority> authorities = new ArrayList<>();
		for (Object role : roles) {
			authorities.add(new SimpleGrantedAuthority(SecurityConfig.ROLE_AUTHORITY_PREFIX + role));
		}
		return authorities;
	}

}
