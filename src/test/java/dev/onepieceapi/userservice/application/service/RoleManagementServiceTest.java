package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.exception.InvalidRoleNameException;
import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.application.port.out.RoleDirectoryPort;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.PermissionDefinition;
import dev.onepieceapi.userservice.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

	private static final User ADMIN = new User(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "luffy",
			"luffy@onepiece.local", AccountStatus.ACTIVE, List.of("ADMIN"), null);

	@Mock
	private RoleDirectoryPort roleDirectoryPort;

	@Mock
	private AuditLogPort auditLogPort;

	@Captor
	private ArgumentCaptor<AuditEvent> auditEventCaptor;

	private RoleManagementService roleManagementService;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var service = new RoleManagementService(this.roleDirectoryPort, this.auditLogPort, clock);
		this.roleManagementService = service;
	}

	@Test
	void normalizesTheNameAndRecordsWhoDidIt() {
		var updated = Map.of("ADMIN", List.<String>of(), "NAVIGATOR", List.<String>of());
		when(this.roleDirectoryPort.createRole("NAVIGATOR", Optional.empty())).thenReturn(updated);

		Map<String, List<String>> result = this.roleManagementService.createRole("  navigator!! ", null, ADMIN);

		assertThat(result).isEqualTo(updated);
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		AuditEvent event = this.auditEventCaptor.getValue();
		assertThat(event.action()).isEqualTo(AuditAction.ROLE_CREATED);
		assertThat(event.targetLabel()).isEqualTo("NAVIGATOR");
		assertThat(event.targetUserId()).isNull();
		assertThat(event.occurredAt()).isEqualTo(NOW);
	}

	@Test
	void passesTheCopyFromRoleThrough() {
		var copyFrom = Optional.of("EDITOR");
		when(this.roleDirectoryPort.createRole("NAVIGATOR", copyFrom)).thenReturn(Map.of());

		this.roleManagementService.createRole("navigator", "EDITOR", ADMIN);

		verify(this.roleDirectoryPort).createRole("NAVIGATOR", Optional.of("EDITOR"));
	}

	@Test
	void rejectsANameThatNormalizesToNothing() {
		assertThatThrownBy(() -> this.roleManagementService.createRole("!!!", null, ADMIN))
			.isInstanceOf(InvalidRoleNameException.class);

		verifyNoInteractions(this.roleDirectoryPort);
		verifyNoInteractions(this.auditLogPort);
	}

	@Test
	void deletesARoleAndRecordsWhoDidIt() {
		this.roleManagementService.deleteRole("NAVIGATOR", ADMIN);

		verify(this.roleDirectoryPort).deleteRole("NAVIGATOR");
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		assertThat(this.auditEventCaptor.getValue().action()).isEqualTo(AuditAction.ROLE_DELETED);
	}

	@Test
	void createsAPermissionAndRecordsWhoDidIt() {
		var created = new PermissionDefinition("docs:approve", "Approve documents");
		when(this.roleDirectoryPort.createPermission("docs:approve", "Approve documents")).thenReturn(created);

		var service = this.roleManagementService;
		PermissionDefinition result = service.createPermission("docs:approve", "Approve documents", ADMIN);

		assertThat(result).isEqualTo(created);
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		assertThat(this.auditEventCaptor.getValue().action()).isEqualTo(AuditAction.PERMISSION_CREATED);
	}

	@Test
	void assignsAPermissionAndRecordsWhoDidIt() {
		this.roleManagementService.assignPermission("EDITOR", "docs:approve", ADMIN);

		verify(this.roleDirectoryPort).assignPermission("EDITOR", "docs:approve");
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		AuditAction action = this.auditEventCaptor.getValue().action();
		assertThat(action).isEqualTo(AuditAction.PERMISSION_ASSIGNED_TO_ROLE);
	}

	@Test
	void revokesAPermissionAndRecordsWhoDidIt() {
		this.roleManagementService.revokePermission("EDITOR", "docs:approve", ADMIN);

		verify(this.roleDirectoryPort).revokePermission("EDITOR", "docs:approve");
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		AuditAction action = this.auditEventCaptor.getValue().action();
		assertThat(action).isEqualTo(AuditAction.PERMISSION_REVOKED_FROM_ROLE);
	}

	@Test
	void deletesAPermissionAndRecordsWhoDidIt() {
		this.roleManagementService.deletePermission("docs:approve", ADMIN);

		verify(this.roleDirectoryPort).deletePermission("docs:approve");
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		assertThat(this.auditEventCaptor.getValue().action()).isEqualTo(AuditAction.PERMISSION_DELETED);
	}

}
