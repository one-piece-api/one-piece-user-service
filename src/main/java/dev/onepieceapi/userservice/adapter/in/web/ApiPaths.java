package dev.onepieceapi.userservice.adapter.in.web;

/**
 * Every REST path this service exposes, as {@code public static final String} constants -
 * plain constants (not enum-backed) because Java annotation attributes require
 * compile-time constant expressions, which an enum accessor is not. Controllers reference
 * these in their mapping annotations; {@code security.SecuredEndpoint} references the
 * same constants to build the authorization rules, so the two can never silently diverge
 * - see {@code docs/adr/0009-permission-based-endpoint-registry.md}.
 */
public final class ApiPaths {

	public static final String HEALTH = "/actuator/health/**";

	public static final String ME = "/me";

	public static final String USERS = "/users";

	public static final String USER_BY_ID = "/users/{userId}";

	public static final String ROLES = "/roles";

	public static final String ROLE_BY_NAME = "/roles/{role}";

	public static final String ROLE_PERMISSION = "/roles/{role}/permissions/{permission}";

	public static final String PERMISSIONS = "/permissions";

	public static final String USER_RESEND_INVITATION = "/users/{userId}/resend-invitation";

	public static final String USER_ROLE = "/users/{userId}/roles/{role}";

	public static final String USER_REVOKE_ACCESS = "/users/{userId}/revoke-access";

	public static final String USER_REACTIVATE = "/users/{userId}/reactivate";

	public static final String AUDIT = "/audit";

	private ApiPaths() {
	}

}
