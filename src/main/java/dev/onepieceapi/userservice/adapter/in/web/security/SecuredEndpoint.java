package dev.onepieceapi.userservice.adapter.in.web.security;

import dev.onepieceapi.userservice.adapter.in.web.ApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

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
@RequiredArgsConstructor
enum SecuredEndpoint {

	HEALTH(HttpMethod.GET, ApiPaths.HEALTH, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::permitAll),
	ME(HttpMethod.GET, ApiPaths.ME, AuthorizeHttpRequestsConfigurer.AuthorizedUrl::authenticated),

	USERS_LIST(HttpMethod.GET, ApiPaths.USERS, permission(Permission.USERS_READ)),
	USER_GET(HttpMethod.GET, ApiPaths.USER_BY_ID, permission(Permission.USERS_READ)),
	ROLES_LIST(HttpMethod.GET, ApiPaths.ROLES, permission(Permission.USERS_READ)),
	USER_INVITE(HttpMethod.POST, ApiPaths.USERS, permission(Permission.USERS_INVITE)),
	USER_RESEND_INVITATION(HttpMethod.POST, ApiPaths.USER_RESEND_INVITATION, permission(Permission.USERS_INVITE)),
	USER_ROLE_ASSIGN(HttpMethod.PUT, ApiPaths.USER_ROLE, permission(Permission.ROLES_WRITE)),
	USER_ROLE_REVOKE(HttpMethod.DELETE, ApiPaths.USER_ROLE, permission(Permission.ROLES_WRITE)),
	USER_REVOKE_ACCESS(HttpMethod.POST, ApiPaths.USER_REVOKE_ACCESS, permission(Permission.ACCESS_WRITE)),
	USER_REACTIVATE(HttpMethod.POST, ApiPaths.USER_REACTIVATE, permission(Permission.ACCESS_WRITE)),
	AUDIT_LIST(HttpMethod.GET, ApiPaths.AUDIT, permission(Permission.AUDIT_READ));

	private final HttpMethod method;

	private final String path;

	private final Consumer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl> rule;

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

	private static Consumer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl> permission(
			Permission permission) {
		return authorizedUrl -> authorizedUrl.hasAuthority(permission.authority());
	}

}
