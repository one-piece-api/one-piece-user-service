package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import dev.onepieceapi.userservice.domain.UserFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

	private static final UUID LUFFY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	private static final String LUFFY_USERNAME = "luffy";

	private static final String LUFFY_EMAIL = "luffy@onepiece.local";

	@Mock
	private UserDirectoryPort userDirectoryPort;

	private UserQueryService userQueryService;

	@BeforeEach
	void setUp() {
		this.userQueryService = new UserQueryService(this.userDirectoryPort);
	}

	@Test
	void listsTheAccountsReturnedByTheIdentityDirectory() {
		Pageable pageable = PageRequest.of(0, 20);
		var roles = List.of("ADMIN");
		var luffy = new User(LUFFY_ID, LUFFY_USERNAME, LUFFY_EMAIL, AccountStatus.ACTIVE, roles, null);
		when(this.userDirectoryPort.findUsers(0, 20, UserFilter.none())).thenReturn(List.of(luffy));
		when(this.userDirectoryPort.countUsers(UserFilter.none())).thenReturn(1L);

		Page<User> page = this.userQueryService.list(pageable, UserFilter.none());

		assertThat(page.getContent()).containsExactly(luffy);
	}

	@Test
	void reportsTheRealmsTotalUserCountRatherThanThisPagesSize() {
		Pageable pageable = PageRequest.of(0, 1);
		var luffy = new User(LUFFY_ID, LUFFY_USERNAME, LUFFY_EMAIL, AccountStatus.ACTIVE, List.of(), null);
		when(this.userDirectoryPort.findUsers(0, 1, UserFilter.none())).thenReturn(List.of(luffy));
		when(this.userDirectoryPort.countUsers(UserFilter.none())).thenReturn(37L);

		Page<User> page = this.userQueryService.list(pageable, UserFilter.none());

		assertThat(page.getTotalElements()).isEqualTo(37L);
		assertThat(page.getContent()).hasSize(1);
	}

	@Test
	void passesTheFilterThroughToTheIdentityDirectory() {
		Pageable pageable = PageRequest.of(0, 20);
		var filter = new UserFilter("nami", RealmRole.EDITOR, AccountStatus.ACTIVE);
		List<String> roles = List.of("EDITOR");
		var nami = new User(LUFFY_ID, "nami", "nami@onepiece.local", AccountStatus.ACTIVE, roles, null);
		when(this.userDirectoryPort.findUsers(0, 20, filter)).thenReturn(List.of(nami));
		when(this.userDirectoryPort.countUsers(filter)).thenReturn(1L);

		Page<User> page = this.userQueryService.list(pageable, filter);

		assertThat(page.getContent()).containsExactly(nami);
		assertThat(page.getTotalElements()).isEqualTo(1L);
	}

	@Test
	void getsASingleUserThroughTheIdentityDirectory() {
		List<String> roles = List.of("ADMIN");
		var luffy = new User(LUFFY_ID, LUFFY_USERNAME, LUFFY_EMAIL, AccountStatus.ACTIVE, roles, null);
		when(this.userDirectoryPort.findUser(LUFFY_ID)).thenReturn(luffy);

		User result = this.userQueryService.getUser(LUFFY_ID);

		assertThat(result).isEqualTo(luffy);
	}

	@Test
	void listsRolePermissionsThroughTheIdentityDirectory() {
		var expected = Map.of(RealmRole.ADMIN, List.of("audit:read", "users:read"), RealmRole.EDITOR,
				List.of("docs:read", "docs:write"));
		when(this.userDirectoryPort.listRolePermissions()).thenReturn(expected);

		Map<RealmRole, List<String>> result = this.userQueryService.listRolePermissions();

		assertThat(result).isEqualTo(expected);
	}

}
