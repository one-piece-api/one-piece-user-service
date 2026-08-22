package dev.onepieceapi.userservice.service;

import dev.onepieceapi.userservice.client.keycloak.KeycloakClient;
import dev.onepieceapi.userservice.controller.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.domain.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserQueryServiceTest {

	private static final String LUFFY_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

	@Mock
	private KeycloakClient keycloakClient;

	private AdminUserQueryService adminUserQueryService;

	@BeforeEach
	void setUp() {
		this.adminUserQueryService = new AdminUserQueryService(this.keycloakClient);
	}

	@Test
	void mapsEachKeycloakAccountToAnAdminSummaryRow() {
		Pageable pageable = PageRequest.of(0, 20);
		UserRepresentation luffy = new UserRepresentation();
		luffy.setId(LUFFY_ID);
		luffy.setEmail("luffy@onepiece.local");
		luffy.setEnabled(true);
		luffy.setRealmRoles(List.of("ADMIN"));
		when(this.keycloakClient.users(0, 20)).thenReturn(List.of(luffy));
		when(this.keycloakClient.count()).thenReturn(1L);

		Page<UserSummaryResponse> page = this.adminUserQueryService.list(pageable);

		assertThat(page.getContent()).singleElement().satisfies(row -> {
			assertThat(row.userId()).isEqualTo(UUID.fromString(LUFFY_ID));
			assertThat(row.email()).isEqualTo("luffy@onepiece.local");
			assertThat(row.status()).isEqualTo(AccountStatus.ACTIVE);
			assertThat(row.roles()).containsExactly("ADMIN");
		});
	}

	@Test
	void reportsTheRealmsTotalUserCountRatherThanThisPagesSize() {
		Pageable pageable = PageRequest.of(0, 1);
		UserRepresentation luffy = new UserRepresentation();
		luffy.setId(LUFFY_ID);
		luffy.setEnabled(true);
		luffy.setRealmRoles(List.of());
		when(this.keycloakClient.users(0, 1)).thenReturn(List.of(luffy));
		when(this.keycloakClient.count()).thenReturn(37L);

		Page<UserSummaryResponse> page = this.adminUserQueryService.list(pageable);

		assertThat(page.getTotalElements()).isEqualTo(37L);
		assertThat(page.getContent()).hasSize(1);
	}

}
