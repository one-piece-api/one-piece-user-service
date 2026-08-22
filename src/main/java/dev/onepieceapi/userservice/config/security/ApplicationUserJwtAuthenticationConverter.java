package dev.onepieceapi.userservice.config.security;

import dev.onepieceapi.userservice.domain.ApplicationUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Resolves a validated JWT to the corresponding {@link ApplicationUser} record, built
 * entirely from the token's own claims - the standard {@code sub} claim, {@code email}
 * and {@code realm_access.roles} - with no local lookup: Keycloak is the sole source of
 * identity (see {@link ApplicationUser}), so any well-formed, validly-signed token for
 * this realm resolves to a user. {@code sub} is Keycloak's own account id, always present
 * on an OIDC token by specification, so no custom protocol mapper/attribute is needed to
 * carry it. A revocation (UF-IDU-13) takes effect at the identity provider immediately
 * but only reaches an already-issued access token once that token's own short lifetime
 * expires and refresh fails: an accepted, bounded window, not a local per-request check.
 * <p>
 * Roles are read from the token's own {@code realm_access.roles} claim: Keycloak
 * recomputes that claim on every token issuance, including the silent refresh
 * oauth2-proxy already performs, so a role change (UF-IDU-15) reaches authorization on
 * the next refresh without needing a local mirror or forcing the user to log out.
 */
@Component
class ApplicationUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final String EMAIL_CLAIM = "email";

	private static final String REALM_ACCESS_CLAIM = "realm_access";

	private static final String REALM_ROLES_CLAIM = "roles";

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UUID userId = JwtUtils.getRequiredUuidClaim(jwt, JwtClaimNames.SUB);
		String email = JwtUtils.getRequiredStringClaim(jwt, EMAIL_CLAIM);

		ApplicationUser applicationUser = new ApplicationUser(userId, email);

		return new ApplicationUserAuthenticationToken(jwt, applicationUser, realmRoleAuthorities(jwt));
	}

	private static List<SimpleGrantedAuthority> realmRoleAuthorities(Jwt jwt) {
		return JwtUtils.getNestedStringListClaim(jwt, REALM_ACCESS_CLAIM, REALM_ROLES_CLAIM)
			.stream()
			.map(role -> new SimpleGrantedAuthority(SecurityConfig.ROLE_AUTHORITY_PREFIX + role))
			.toList();
	}

}
