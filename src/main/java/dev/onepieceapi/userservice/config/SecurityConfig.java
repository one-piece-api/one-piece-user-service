package dev.onepieceapi.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Every request is authenticated against Keycloak (realm "onepiece") as a JWT-based
 * OAuth2 resource server, except the Kubernetes health probes. Roles come from the
 * token's "realm_access.roles" claim (see
 * onepiece-infrastructure/keycloak/realm-onepiece.json), mapped to Spring Security
 * authorities with the conventional "ROLE_" prefix.
 */
@Configuration
public class SecurityConfig {

	/**
	 * The Spring Security convention {@code hasRole(...)} relies on: it checks for a
	 * {@code GrantedAuthority} named "ROLE_" + the role. Shared with
	 * {@link dev.onepieceapi.userservice.identity.MeController}, which strips it back off
	 * before returning roles as product data.
	 */
	public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

	private static final String HEALTH_PROBE_PATH = "/actuator/health/**";

	private static final String REALM_ACCESS_CLAIM = "realm_access";

	private static final String REALM_ROLES_CLAIM = "roles";

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
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRoleAuthorities);
		jwt.jwtAuthenticationConverter(converter);
	}

	static Collection<GrantedAuthority> realmRoleAuthorities(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
		if (realmAccess == null || !(realmAccess.get(REALM_ROLES_CLAIM) instanceof List<?> roles)) {
			return List.of();
		}
		List<GrantedAuthority> authorities = new ArrayList<>();
		for (Object role : roles) {
			authorities.add(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role));
		}
		return authorities;
	}

}
