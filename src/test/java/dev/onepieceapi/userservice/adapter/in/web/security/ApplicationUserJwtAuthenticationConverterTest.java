package dev.onepieceapi.userservice.adapter.in.web.security;

import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationUserJwtAuthenticationConverterTest {

	private static final UUID USER_ID = UUID.fromString("446fbe79-5cc4-458d-925d-9934334b6dcf");

	private static final String USERNAME = "luffy";

	private static final String EMAIL = "luffy@onepiece.local";

	private ApplicationUserJwtAuthenticationConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new ApplicationUserJwtAuthenticationConverter();
	}

	@Test
	void resolvesTheApplicationUserAndAuthoritiesFromTheTokensOwnClaims() {
		Jwt jwt = jwtWithSubjectUsernameEmailAndRoles(USER_ID, USERNAME, EMAIL, "ADMIN", "EDITOR");
		var authentication = this.converter.convert(jwt);

		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_EDITOR");
		var roles = List.of("ADMIN", "EDITOR");
		var expected = new User(USER_ID, USERNAME, EMAIL, AccountStatus.ACTIVE, roles, null);
		assertThat(((ApplicationUserAuthenticationToken) authentication).getUser()).isEqualTo(expected);
	}

	@Test
	void alsoResolvesPermissionAuthoritiesFromTheResourceAccessClaim() {
		Map<String, Object> realmAccess = Map.of("roles", List.of("ADMIN"));
		Map<String, Object> resourceAccess = Map.of("onepiece-proxy",
				Map.of("roles", List.of("users:read", "audit:read")));
		Map<String, Object> claims = new HashMap<>();
		claims.put("sub", USER_ID.toString());
		claims.put("preferred_username", USERNAME);
		claims.put("email", EMAIL);
		claims.put("realm_access", realmAccess);
		claims.put("resource_access", resourceAccess);
		Jwt jwt = jwtWithClaims(claims);

		var authentication = this.converter.convert(jwt);

		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("ROLE_ADMIN", "PERMISSION_users:read", "PERMISSION_audit:read");
	}

	@Test
	void resolvesNoPermissionAuthoritiesWhenTheResourceAccessClaimIsAbsent() {
		Jwt jwt = jwtWithSubjectUsernameEmailAndRoles(USER_ID, USERNAME, EMAIL, "ADMIN");

		var authentication = this.converter.convert(jwt);

		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactly("ROLE_ADMIN");
	}

	@Test
	void rejectsATokenMissingTheSubjectClaim() {
		Jwt jwt = jwtWithClaims(Map.of("preferred_username", USERNAME, "email", EMAIL));

		assertThatThrownBy(() -> this.converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void rejectsATokenMissingTheUsernameClaim() {
		Jwt jwt = jwtWithClaims(Map.of("sub", USER_ID.toString(), "email", EMAIL));

		assertThatThrownBy(() -> this.converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void rejectsATokenMissingTheEmailClaim() {
		Jwt jwt = jwtWithClaims(Map.of("sub", USER_ID.toString(), "preferred_username", USERNAME));

		assertThatThrownBy(() -> this.converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

	private static Jwt jwtWithSubjectUsernameEmailAndRoles(UUID userId, String username, String email,
			String... roles) {
		Map<String, Object> realmAccess = Map.of("roles", List.of(roles));
		return jwtWithClaims(Map.of("sub", userId.toString(), "preferred_username", username, "email", email,
				"realm_access", realmAccess));
	}

	private static Jwt jwtWithClaims(Map<String, Object> claims) {
		return Jwt.withTokenValue("token")
			.header("alg", "none")
			.issuedAt(Instant.EPOCH)
			.expiresAt(Instant.EPOCH.plusSeconds(300))
			.claims(c -> c.putAll(claims))
			.build();
	}

}
