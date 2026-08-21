package dev.onepieceapi.userservice.config.security;

import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.ApplicationUser;
import dev.onepieceapi.userservice.exception.ApplicationUserNotFoundException;
import dev.onepieceapi.userservice.service.ApplicationUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationUserJwtAuthenticationConverterTest {

	private static final UUID USER_ID = UUID.fromString("446fbe79-5cc4-458d-925d-9934334b6dcf");

	@Mock
	private ApplicationUserService applicationUserService;

	private ApplicationUserJwtAuthenticationConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new ApplicationUserJwtAuthenticationConverter(this.applicationUserService);
	}

	@Test
	void resolvesAuthoritiesFromTheTokensRealmRoles() {
		ApplicationUser user = new ApplicationUser(USER_ID, "luffy@onepiece.local", AccountStatus.ACTIVE);
		when(this.applicationUserService.findByUserId(USER_ID)).thenReturn(user);

		var authentication = this.converter.convert(jwtWithUserIdAndRoles(USER_ID, "ADMIN", "EDITOR"));

		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_EDITOR");
		assertThat(((ApplicationUserAuthenticationToken) authentication).getApplicationUser()).isEqualTo(user);
	}

	@Test
	void rejectsATokenForADisabledUser() {
		ApplicationUser user = new ApplicationUser(USER_ID, "luffy@onepiece.local", AccountStatus.DISABLED);
		when(this.applicationUserService.findByUserId(USER_ID)).thenReturn(user);

		assertThatThrownBy(() -> this.converter.convert(jwtWithUserIdAndRoles(USER_ID, "ADMIN")))
			.isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void rejectsATokenThatDoesNotResolveToAKnownUser() {
		when(this.applicationUserService.findByUserId(USER_ID))
			.thenThrow(new ApplicationUserNotFoundException(USER_ID));

		assertThatThrownBy(() -> this.converter.convert(jwtWithUserIdAndRoles(USER_ID, "ADMIN")))
			.isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void rejectsATokenMissingTheUserIdClaim() {
		Jwt jwt = jwtWithClaims(Map.of());

		assertThatThrownBy(() -> this.converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

	private static Jwt jwtWithUserIdAndRoles(UUID userId, String... roles) {
		Map<String, Object> realmAccess = Map.of("roles", List.of(roles));
		return jwtWithClaims(Map.of("userId", userId.toString(), "realm_access", realmAccess));
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
