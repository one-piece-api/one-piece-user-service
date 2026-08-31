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
 * <p>
 * Endpoint-level authorization is driven entirely by {@link SecuredEndpoint} - every
 * secured path's method, path and required permission live there, not as ad hoc rules
 * here; see {@code docs/adr/0009-permission-based-endpoint-registry.md}.
 */
@Configuration
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class SecurityConfig {

	private final ApplicationUserJwtAuthenticationConverter jwtAuthenticationConverter;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				SecuredEndpoint.configureAll(auth);
				// Backstop only: every real endpoint is enumerated in SecuredEndpoint, so
				// this covers anything not yet added to it (e.g. Spring Boot's internal
				// "/error" forward) - authenticated, not denyAll, to avoid turning a
				// plain
				// 404/500 into a confusing 403 for those edge cases.
				auth.anyRequest().authenticated();
			})
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(this::configureJwt));
		return http.build();
	}

	private void configureJwt(OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer jwt) {
		jwt.jwtAuthenticationConverter(this.jwtAuthenticationConverter);
	}

}
