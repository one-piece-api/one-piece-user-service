package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.RoleDirectoryPort;
import dev.onepieceapi.userservice.domain.PermissionDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleQueryServiceTest {

	@Mock
	private RoleDirectoryPort roleDirectoryPort;

	private RoleQueryService roleQueryService;

	@BeforeEach
	void setUp() {
		this.roleQueryService = new RoleQueryService(this.roleDirectoryPort);
	}

	@Test
	void listsRolePermissionsThroughTheDirectory() {
		var expected = Map.of("ADMIN", List.of("audit:read", "users:read"), "EDITOR",
				List.of("docs:read", "docs:write"));
		when(this.roleDirectoryPort.listRoles()).thenReturn(expected);

		Map<String, List<String>> result = this.roleQueryService.listRoles();

		assertThat(result).isEqualTo(expected);
	}

	@Test
	void listsEveryPermissionThroughTheDirectory() {
		var expected = List.of(new PermissionDefinition("users:read", "List and view crew members"),
				new PermissionDefinition("docs:approve", "Approve documents"));
		when(this.roleDirectoryPort.listPermissions()).thenReturn(expected);

		List<PermissionDefinition> result = this.roleQueryService.listPermissions();

		assertThat(result).isEqualTo(expected);
	}

}
