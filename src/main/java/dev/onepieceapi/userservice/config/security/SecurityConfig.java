package dev.onepieceapi.userservice.config;

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
 * resolves the token to the application's own user record and builds authorities from its
 * *current* roles (UF-IDU-10) — not from the token's own "realm_access.roles" claim.
 */
@Configuration
public class SecurityConfig {

	/**
	 * The Spring Security convention {@code hasRole(...)} relies on: it checks for a
	 * {@code GrantedAuthority} named "ROLE_" + the role.
	 */
	public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

	private static final String HEALTH_PROBE_PATH = "/actuator/health/**";

	private final ApplicationUserJwtAuthenticationConverter jwtAuthenticationConverter;

	SecurityConfig(ApplicationUserJwtAuthenticationConverter jwtAuthenticationConverter) {
		this.jwtAuthenticationConverter = jwtAuthenticationConverter;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				auth.requestMatchers(HEALTH_PROBE_PATH).permitAll();
				auth.anyRequest().authenticated();
			})
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(this::configureJwt));
		return http.build();
	}

	private void configureJwt(OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer jwt) {
		jwt.jwtAuthenticationConverter(this.jwtAuthenticationConverter);
	}

}
