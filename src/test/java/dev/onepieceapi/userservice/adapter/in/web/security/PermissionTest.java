package dev.onepieceapi.userservice.adapter.in.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

	@Test
	void authorityPrefixesTheValueForSpringSecurity() {
		assertThat(Permission.USERS_READ.authority()).isEqualTo("PERMISSION_users:read");
	}

	@Test
	void allFromKeepsOnlyPermissionAuthoritiesAndStripsThePrefix() {
		var role = new SimpleGrantedAuthority("ROLE_ADMIN");
		var usersRead = new SimpleGrantedAuthority("PERMISSION_users:read");
		var auditRead = new SimpleGrantedAuthority("PERMISSION_audit:read");

		var permissions = Permission.allFrom(Set.of(role, usersRead, auditRead));

		assertThat(permissions).containsExactlyInAnyOrder("users:read", "audit:read");
	}

	@Test
	void allFromReturnsEmptyWhenNoAuthorityIsAPermission() {
		var role = new SimpleGrantedAuthority("ROLE_EDITOR");

		var permissions = Permission.allFrom(Set.of(role));

		assertThat(permissions).isEmpty();
	}

}
