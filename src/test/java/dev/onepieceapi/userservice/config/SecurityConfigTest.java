package dev.onepieceapi.userservice.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

	@Test
	void mapsRealmRolesToPrefixedAuthorities() {
		Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("ADMIN", "EDITOR"))));

		assertThat(SecurityConfig.realmRoleAuthorities(jwt)).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_EDITOR");
	}

	@Test
	void returnsNoAuthoritiesWhenRealmAccessClaimIsMissing() {
		Jwt jwt = jwtWithClaims(Map.of());

		assertThat(SecurityConfig.realmRoleAuthorities(jwt)).isEmpty();
	}

	@Test
	void returnsNoAuthoritiesWhenRolesKeyIsMissing() {
		Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of()));

		assertThat(SecurityConfig.realmRoleAuthorities(jwt)).isEmpty();
	}

	private static Jwt jwtWithClaims(Map<String, Object> extraClaims) {
		return Jwt.withTokenValue("token")
			.header("alg", "none")
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.claims(claims -> claims.putAll(extraClaims))
			.build();
	}

}
