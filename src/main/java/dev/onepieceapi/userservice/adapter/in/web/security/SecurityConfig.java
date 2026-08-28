package dev.onepieceapi.userservice.adapter.in.web.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every request is authenticated against Keycloak (realm "onepiece") as a JWT-based
 * OAuth2 resource server, except the Kubernetes health probes. Beyond
 * signature/expiry/issuer validation, {@link ApplicationUserJwtAuthenticationConverter}
 * resolves the token to the application's own user record to enforce status (UF-IDU-10),
 * rejecting a DISABLED user even with an otherwise still-valid token, while authorities
 * are built from the token's own "realm_access.roles" claim, which Keycloak recomputes on
 * every issuance.
 */
@Configuration
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class SecurityConfig {

	/**
	 * The Spring Security convention {@code hasRole(...)} relies on: it checks for a
	 * {@code GrantedAuthority} named "ROLE_" + the role.
	 */
	public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

	/**
	 * Permission authorities (e.g. {@code PERMISSION_users:read}), sourced from the JWT's
	 * {@code resource_access} claim - see
	 * {@link ApplicationUserJwtAuthenticationConverter} and
	 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}. Kept distinct
	 * from {@link #ROLE_AUTHORITY_PREFIX} so {@code hasRole}/{@code hasAuthority} checks
	 * never accidentally match the wrong kind.
	 */
	public static final String PERMISSION_AUTHORITY_PREFIX = "PERMISSION_";

	private static final String HEALTH_PROBE_PATH = "/actuator/health/**";

	private static final String ADMIN_PATH = "/admin/**";

	private final ApplicationUserJwtAuthenticationConverter jwtAuthenticationConverter;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				auth.requestMatchers(HEALTH_PROBE_PATH).permitAll();
				auth.requestMatchers(ADMIN_PATH).hasRole("ADMIN");
				auth.anyRequest().authenticated();
			})
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(this::configureJwt));
		return http.build();
	}

	private void configureJwt(OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer jwt) {
		jwt.jwtAuthenticationConverter(this.jwtAuthenticationConverter);
	}

}
