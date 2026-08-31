package dev.onepieceapi.userservice.adapter.in.web.security;

import lombok.RequiredArgsConstructor;

/**
 * The closed set of fine-grained permissions {@link SecuredEndpoint} authorizes against -
 * see {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md} for how these
 * strings are composited into realm roles in Keycloak, and
 * {@code docs/adr/0009-permission-based-endpoint-registry.md} for why they are an enum
 * here specifically (a closed set only {@code SecurityConfig} reasons about) despite
 * ADR-0007 deliberately keeping permissions as opaque strings on the
 * JWT-claim/wire-format path.
 */
@RequiredArgsConstructor
public enum Permission {

	USERS_READ("users:read"),

	USERS_INVITE("users:invite"),

	ROLES_WRITE("roles:write"),

	ACCESS_WRITE("access:write"),

	AUDIT_READ("audit:read");

	private final String value;

	public String authority() {
		return SecurityConfig.PERMISSION_AUTHORITY_PREFIX + this.value;
	}

}
