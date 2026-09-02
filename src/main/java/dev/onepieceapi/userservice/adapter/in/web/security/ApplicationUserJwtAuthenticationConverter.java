package dev.onepieceapi.userservice.adapter.in.web.security;

import dev.onepieceapi.userservice.config.KeycloakRoleProperties;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
 * the next refresh without needing a local mirror or forcing the user to log out.
 * Keycloak always includes its own auto-assigned {@code default-roles-<realm>} in this
 * claim for every account, regardless of realm configuration - filtered out here via
 * {@link KeycloakRoleProperties#excludedRealmRoles}, the same list the Admin API adapters
 * use, so it never reaches {@code /me} or an authorization check. The same filtered list
 * backs both the resolved {@link User#roles()} and the Spring Security authorities.
 * <p>
 * Permissions (see {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}) are
 * read the same way from {@code resource_access.onepiece-proxy.roles} - Keycloak expands
 * a role's composite client-roles into this claim automatically, so no extra lookup is
 * needed here either. They are exposed only as
 * {@link Permission#AUTHORITY_PREFIX}-prefixed authorities, not on {@link User} itself -
 * see {@link Permission#allFrom} for where and why they are read back out.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class ApplicationUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final String USERNAME_CLAIM = "preferred_username";

	private static final String EMAIL_CLAIM = "email";

	private static final String REALM_ACCESS_CLAIM = "realm_access";

	private static final String RESOURCE_ACCESS_CLAIM = "resource_access";

	/**
	 * The key holding a role-name list under both {@link #REALM_ACCESS_CLAIM} and a
	 * {@link #RESOURCE_ACCESS_CLAIM} client entry.
	 */
	private static final String ROLES_CLAIM = "roles";

	/** The Keycloak client whose roles this application treats as permissions. */
	private static final String PERMISSIONS_CLIENT_ID = "onepiece-proxy";

	/**
	 * The Spring Security convention {@code hasRole(...)} relies on: it checks for a
	 * {@code GrantedAuthority} named "ROLE_" + the role.
	 */
	private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

	private final KeycloakRoleProperties keycloakRoleProperties;

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UUID userId = JwtUtils.getRequiredUuidClaim(jwt, JwtClaimNames.SUB);
		String username = JwtUtils.getRequiredStringClaim(jwt, USERNAME_CLAIM);
		String email = JwtUtils.getRequiredStringClaim(jwt, EMAIL_CLAIM);
		var roles = JwtUtils.getNestedStringListClaim(jwt, REALM_ACCESS_CLAIM, ROLES_CLAIM)
			.stream()
			.filter(role -> !this.keycloakRoleProperties.excludedRealmRoles().contains(role))
			.toList();
		var permissions = JwtUtils.getNestedStringListClaim(jwt, RESOURCE_ACCESS_CLAIM, PERMISSIONS_CLIENT_ID,
				ROLES_CLAIM);

		User user = new User(userId, username, email, AccountStatus.ACTIVE, roles, null);

		return new ApplicationUserAuthenticationToken(jwt, user, authorities(roles, permissions));
	}

	private static Set<SimpleGrantedAuthority> authorities(List<String> roles, List<String> permissions) {
		Stream<SimpleGrantedAuthority> roleAuthorities = roles.stream()
			.map(role -> new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role));
		String permissionPrefix = Permission.AUTHORITY_PREFIX;
		Stream<SimpleGrantedAuthority> permissionAuthorities = permissions.stream()
			.map(permission -> new SimpleGrantedAuthority(permissionPrefix + permission));
		return Stream.concat(roleAuthorities, permissionAuthorities).collect(Collectors.toSet());
	}

}
