package dev.onepieceapi.userservice.adapter.in.web.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The closed set of fine-grained permissions {@link SecuredEndpoint} authorizes against -
 * see {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md} for how these
 * strings are composited into realm roles in Keycloak, and
 * {@code docs/adr/0009-permission-based-endpoint-registry.md} for why they are an enum
 * here specifically (a closed set only this package reasons about) despite ADR-0007
 * deliberately keeping permissions as opaque strings on the JWT-claim/wire-format path.
 */
@RequiredArgsConstructor
public enum Permission {

	USERS_READ("users:read"),

	USERS_INVITE("users:invite"),

	ROLES_READ("roles:read"),

	ROLES_ASSIGN("roles:assign"),

	ACCESS_WRITE("access:write"),

	AUDIT_READ("audit:read");

	/**
	 * The Spring Security convention this application follows for a permission authority
	 * (e.g. {@code PERMISSION_users:read}), sourced from the JWT's
	 * {@code resource_access} claim - see
	 * {@link ApplicationUserJwtAuthenticationConverter} and
	 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}. Kept distinct
	 * from the {@code ROLE_} prefix Spring Security's own {@code hasRole(...)} relies on,
	 * so {@code hasRole}/{@code hasAuthority} checks never accidentally match the wrong
	 * kind.
	 */
	public static final String AUTHORITY_PREFIX = "PERMISSION_";

	private final String value;

	public String authority() {
		return AUTHORITY_PREFIX + this.value;
	}

	/**
	 * The permission strings (without {@link #AUTHORITY_PREFIX}) carried by the given
	 * granted authorities - permissions live only as prefixed authorities, never on
	 * {@link dev.onepieceapi.userservice.domain.User} itself: unlike roles, they are
	 * meaningful only for the caller's own token, never for another user looked up
	 * through the Admin API, so a field every {@code User} would carry (always empty on
	 * that path) would be the wrong shape.
	 */
	public static List<String> allFrom(Collection<? extends GrantedAuthority> authorities) {
		return authorities.stream()
			.map(GrantedAuthority::getAuthority)
			.filter(Objects::nonNull)
			.filter(authority -> authority.startsWith(AUTHORITY_PREFIX))
			.map(authority -> authority.substring(AUTHORITY_PREFIX.length()))
			.toList();
	}

}
