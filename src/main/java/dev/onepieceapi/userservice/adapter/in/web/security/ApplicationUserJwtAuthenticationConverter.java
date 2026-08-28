package dev.onepieceapi.userservice.adapter.in.web.security;

import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.User;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves a validated JWT to the corresponding {@link User}, built entirely from the
 * token's own claims - the standard {@code sub} claim, {@code preferred_username},
 * {@code email} and {@code realm_access.roles} - with no local lookup: Keycloak is the
 * sole source of identity (see {@link User}), so any well-formed, validly-signed token
 * for this realm resolves to a user. {@code sub} is Keycloak's own account id, always
 * present on an OIDC token by specification, so no custom protocol mapper/attribute is
 * needed to carry it - unlike {@code preferred_username}, which this realm's custom
 * {@code profile} client scope must explicitly map from the account's {@code username}
 * attribute (see {@code onepiece-infrastructure/keycloak/realm-onepiece.json}), the
 * user-chosen handle from UPDATE_PROFILE at activation (UF-IDU-02) and the identifier the
 * UI displays in place of email (§2 of application-user-identity-management.md). A
 * revocation (UF-IDU-13) takes effect at the identity provider immediately but only
 * reaches an already-issued access token once that token's own short lifetime expires and
 * refresh fails: an accepted, bounded window, not a local per-request check - so
 * {@code status} is always resolved to {@link AccountStatus#ACTIVE} here rather than
 * tracked live (see {@link User}'s own javadoc for why that's the only value it could
 * ever meaningfully have at this point).
 * <p>
 * Roles are read from the token's own {@code realm_access.roles} claim: Keycloak
 * recomputes that claim on every token issuance, including the silent refresh
 * oauth2-proxy already performs, so a role change (UF-IDU-15) reaches authorization on
 * the next refresh without needing a local mirror or forcing the user to log out. The
 * same list backs both the resolved {@link User#roles()} and the Spring Security
 * authorities.
 * <p>
 * Permissions (see {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}) are
 * read the same way from {@code resource_access.onepiece-proxy.roles} - Keycloak expands
 * a role's composite client-roles into this claim automatically, so no extra lookup is
 * needed here either. They are exposed only as {@code PERMISSION_}-prefixed authorities,
 * not on {@link User} itself: unlike roles, permissions are meaningful only for the
 * caller's own token, never for another user looked up through the Admin API (see
 * {@code MeController}, the only place they are read back out).
 */
@Component
class ApplicationUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final String USERNAME_CLAIM = "preferred_username";

	private static final String EMAIL_CLAIM = "email";

	private static final String REALM_ACCESS_CLAIM = "realm_access";

	private static final String REALM_ROLES_CLAIM = "roles";

	/** The Keycloak client whose roles this application treats as permissions. */
	private static final String PERMISSIONS_CLIENT_ID = "onepiece-proxy";

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UUID userId = JwtUtils.getRequiredUuidClaim(jwt, JwtClaimNames.SUB);
		String username = JwtUtils.getRequiredStringClaim(jwt, USERNAME_CLAIM);
		String email = JwtUtils.getRequiredStringClaim(jwt, EMAIL_CLAIM);
		List<String> roles = JwtUtils.getNestedStringListClaim(jwt, REALM_ACCESS_CLAIM, REALM_ROLES_CLAIM);
		List<String> permissions = JwtUtils.getResourceAccessClientRolesClaim(jwt, PERMISSIONS_CLIENT_ID);

		User user = new User(userId, username, email, AccountStatus.ACTIVE, roles, null);

		return new ApplicationUserAuthenticationToken(jwt, user, authorities(roles, permissions));
	}

	private static Set<SimpleGrantedAuthority> authorities(List<String> roles, List<String> permissions) {
		Stream<SimpleGrantedAuthority> roleAuthorities = roles.stream()
			.map(role -> new SimpleGrantedAuthority(SecurityConfig.ROLE_AUTHORITY_PREFIX + role));
		String permissionPrefix = SecurityConfig.PERMISSION_AUTHORITY_PREFIX;
		Stream<SimpleGrantedAuthority> permissionAuthorities = permissions.stream()
			.map(permission -> new SimpleGrantedAuthority(permissionPrefix + permission));
		return Stream.concat(roleAuthorities, permissionAuthorities).collect(Collectors.toSet());
	}

}
