package dev.onepieceapi.userservice.adapter.in.web.security;

import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationUserJwtAuthenticationConverterTest {

	private static final UUID USER_ID = UUID.fromString("446fbe79-5cc4-458d-925d-9934334b6dcf");

	private static final String EMAIL = "luffy@onepiece.local";

	private ApplicationUserJwtAuthenticationConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new ApplicationUserJwtAuthenticationConverter();
	}

	@Test
	void resolvesTheApplicationUserAndAuthoritiesFromTheTokensOwnClaims() {
		Jwt jwt = jwtWithSubjectEmailAndRoles(USER_ID, EMAIL, "ADMIN", "EDITOR");
		var authentication = this.converter.convert(jwt);

		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_EDITOR");
		assertThat(((ApplicationUserAuthenticationToken) authentication).getUser())
			.isEqualTo(new User(USER_ID, EMAIL, AccountStatus.ACTIVE, List.of("ADMIN", "EDITOR"), null));
	}

	@Test
	void rejectsATokenMissingTheSubjectClaim() {
		Jwt jwt = jwtWithClaims(Map.of("email", EMAIL));

		assertThatThrownBy(() -> this.converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void rejectsATokenMissingTheEmailClaim() {
		Jwt jwt = jwtWithClaims(Map.of("sub", USER_ID.toString()));

		assertThatThrownBy(() -> this.converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

	private static Jwt jwtWithSubjectEmailAndRoles(UUID userId, String email, String... roles) {
		Map<String, Object> realmAccess = Map.of("roles", List.of(roles));
		return jwtWithClaims(Map.of("sub", userId.toString(), "email", email, "realm_access", realmAccess));
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
