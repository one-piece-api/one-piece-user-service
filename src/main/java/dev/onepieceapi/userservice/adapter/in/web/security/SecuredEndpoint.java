package dev.onepieceapi.userservice.adapter.in.web.security;

import dev.onepieceapi.userservice.adapter.in.web.ApiPaths;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Every secured endpoint, in one place: its HTTP method, its path (from {@link ApiPaths},
 * so it can never drift from what the controller actually maps) and the rule that
 * authorizes it. {@link SecurityConfig} only ever calls {@link #configureAll}, staying
 * unaware of how the registry itself is built - adding a new secured endpoint is a single
 * new constant here plus the matching {@link ApiPaths} constant, not a bespoke
 * {@code requestMatchers(...)} call to remember. See
 * {@code docs/adr/0009-permission-based-endpoint-registry.md}.
 */
enum SecuredEndpoint {

	HEALTH(HttpMethod.GET, ApiPaths.HEALTH, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::permitAll),
	// The API contract and its Swagger UI: any signed-in user, like the SPA itself -
	// every operation "Try it out" calls is still authorized by its own entry below.
	API_DOCS(HttpMethod.GET, ApiPaths.API_DOCS, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),
	SWAGGER_UI(HttpMethod.GET, ApiPaths.SWAGGER_UI, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),
	SWAGGER_UI_ENTRY(HttpMethod.GET, ApiPaths.SWAGGER_UI_ENTRY,
			AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),
	ME(HttpMethod.GET, ApiPaths.ME, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),

	USERS_LIST(HttpMethod.GET, ApiPaths.USERS, Permission.USERS_READ),
	USER_GET(HttpMethod.GET, ApiPaths.USER_BY_ID, Permission.USERS_READ),
	ROLES_LIST(HttpMethod.GET, ApiPaths.ROLES, Permission.ROLES_READ),
	ROLE_CREATE(HttpMethod.POST, ApiPaths.ROLES, Permission.ROLES_MANAGE),
	ROLE_DELETE(HttpMethod.DELETE, ApiPaths.ROLE_BY_NAME, Permission.ROLES_MANAGE),
	PERMISSIONS_LIST(HttpMethod.GET, ApiPaths.PERMISSIONS, Permission.ROLES_MANAGE),
	PERMISSION_CREATE(HttpMethod.POST, ApiPaths.PERMISSIONS, Permission.ROLES_MANAGE),
	PERMISSION_DELETE(HttpMethod.DELETE, ApiPaths.PERMISSION_BY_KEY, Permission.ROLES_MANAGE),
	ROLE_PERMISSION_ASSIGN(HttpMethod.PUT, ApiPaths.ROLE_PERMISSION, Permission.ROLES_MANAGE),
	ROLE_PERMISSION_REVOKE(HttpMethod.DELETE, ApiPaths.ROLE_PERMISSION, Permission.ROLES_MANAGE),
	USER_INVITE(HttpMethod.POST, ApiPaths.USERS, Permission.USERS_INVITE),
	USER_RESEND_INVITATION(HttpMethod.POST, ApiPaths.USER_RESEND_INVITATION, Permission.USERS_INVITE),
	USER_ROLE_ASSIGN(HttpMethod.PUT, ApiPaths.USER_ROLE, Permission.ROLES_ASSIGN),
	USER_ROLE_REVOKE(HttpMethod.DELETE, ApiPaths.USER_ROLE, Permission.ROLES_ASSIGN),
	USER_REVOKE_ACCESS(HttpMethod.POST, ApiPaths.USER_REVOKE_ACCESS, Permission.ACCESS_WRITE),
	USER_REACTIVATE(HttpMethod.POST, ApiPaths.USER_REACTIVATE, Permission.ACCESS_WRITE),
	AUDIT_LIST(HttpMethod.GET, ApiPaths.AUDIT, Permission.AUDIT_READ),
	AUDIT_ACTORS_LIST(HttpMethod.GET, ApiPaths.AUDIT_ACTORS, Permission.AUDIT_READ);

	private final HttpMethod method;

	private final String path;

	private final Consumer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl> rule;

	/**
	 * The permission this endpoint requires, or null when the rule isn't
	 * permission-based.
	 */
	private final Permission permission;

	SecuredEndpoint(HttpMethod method, String path,
			Consumer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl> rule) {
		this.method = method;
		this.path = path;
		this.rule = rule;
		this.permission = null;
	}

	SecuredEndpoint(HttpMethod method, String path, Permission permission) {
		this.method = method;
		this.path = path;
		this.rule = authorizedUrl -> authorizedUrl.hasAuthority(permission.authority());
		this.permission = permission;
	}

	/**
	 * Applies every constant's rule to the given registry - the one entry point
	 * {@link SecurityConfig} calls, so it never has to know this registry is backed by an
	 * enum, let alone loop over it itself.
	 */
	static void configureAll(
			AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reg) {
		for (SecuredEndpoint endpoint : values()) {
			var authorizedUrl = reg.requestMatchers(endpoint.method, endpoint.path);
			endpoint.rule.accept(authorizedUrl);
		}
	}

	/**
	 * The permission required by the endpoint mapped at exactly this method and path
	 * template (as written in {@link ApiPaths}), if any - what
	 * {@link RequiredPermissionOpenApiCustomizer} documents on each OpenAPI operation, so
	 * the published contract is derived from the same registry that enforces it.
	 */
	static Optional<Permission> requiredPermission(HttpMethod method, String path) {
		return Arrays.stream(values())
			.filter(endpoint -> endpoint.method.equals(method) && endpoint.path.equals(path))
			.findFirst()
			.map(endpoint -> endpoint.permission);
	}

}
