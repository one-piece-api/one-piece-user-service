package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

	private static final UUID LUFFY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	private static final String LUFFY_EMAIL = "luffy@onepiece.local";

	@Mock
	private UserDirectoryPort userDirectoryPort;

	private AdminUserQueryService adminUserQueryService;

	@BeforeEach
	void setUp() {
		this.adminUserQueryService = new AdminUserQueryService(this.userDirectoryPort);
	}

	@Test
	void listsTheAccountsReturnedByTheIdentityDirectory() {
		Pageable pageable = PageRequest.of(0, 20);
		var luffy = new User(LUFFY_ID, LUFFY_EMAIL, AccountStatus.ACTIVE, List.of("ADMIN"), null);
		when(this.userDirectoryPort.findUsers(0, 20)).thenReturn(List.of(luffy));
		when(this.userDirectoryPort.countUsers()).thenReturn(1L);

		Page<User> page = this.adminUserQueryService.list(pageable);

		assertThat(page.getContent()).containsExactly(luffy);
	}

	@Test
	void reportsTheRealmsTotalUserCountRatherThanThisPagesSize() {
		Pageable pageable = PageRequest.of(0, 1);
		var luffy = new User(LUFFY_ID, LUFFY_EMAIL, AccountStatus.ACTIVE, List.of(), null);
		when(this.userDirectoryPort.findUsers(0, 1)).thenReturn(List.of(luffy));
		when(this.userDirectoryPort.countUsers()).thenReturn(37L);

		Page<User> page = this.adminUserQueryService.list(pageable);

		assertThat(page.getTotalElements()).isEqualTo(37L);
		assertThat(page.getContent()).hasSize(1);
	}

}
