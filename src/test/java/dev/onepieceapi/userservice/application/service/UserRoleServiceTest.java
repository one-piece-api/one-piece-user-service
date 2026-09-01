package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.exception.RoleNotFoundException;
import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.application.port.out.RoleDirectoryPort;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

	private static final User ADMIN = new User(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "luffy",
			"luffy@onepiece.local", AccountStatus.ACTIVE, List.of("ADMIN"), null);

	private static final UUID TARGET_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

	private static final String TARGET_EMAIL = "usopp@onepiece.local";

	private static final Map<String, List<String>> ROLE_CATALOG = Map.of("ADMIN", List.of(), "EDITOR", List.of());

	@Mock
	private UserDirectoryPort userDirectoryPort;

	@Mock
	private RoleDirectoryPort roleDirectoryPort;

	@Mock
	private AuditLogPort auditLogPort;

	@Captor
	private ArgumentCaptor<AuditEvent> auditEventCaptor;

	private UserRoleService userRoleService;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var directory = this.userDirectoryPort;
		this.userRoleService = new UserRoleService(directory, this.roleDirectoryPort, this.auditLogPort, clock);
	}

	@Test
	void assignsARoleAndRecordsWhoDidIt() {
		when(this.roleDirectoryPort.listRoles()).thenReturn(ROLE_CATALOG);
		var updated = targetUser(List.of("EDITOR", "ADMIN"));
		when(this.userDirectoryPort.assignRole(TARGET_ID, "ADMIN")).thenReturn(updated);

		User result = this.userRoleService.assignRole(TARGET_ID, "ADMIN", ADMIN);

		assertThat(result).isEqualTo(updated);
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		AuditEvent event = this.auditEventCaptor.getValue();
		assertThat(event.action()).isEqualTo(AuditAction.ROLE_ASSIGNED);
		assertThat(event.actorUserId()).isEqualTo(ADMIN.userId());
		assertThat(event.targetUserId()).isEqualTo(TARGET_ID);
		assertThat(event.targetLabel()).isEqualTo("ADMIN");
		assertThat(event.occurredAt()).isEqualTo(NOW);
	}

	@Test
	void revokesARoleAndRecordsWhoDidIt() {
		when(this.roleDirectoryPort.listRoles()).thenReturn(ROLE_CATALOG);
		var updated = targetUser(List.of("EDITOR"));
		when(this.userDirectoryPort.revokeRole(TARGET_ID, "ADMIN")).thenReturn(updated);

		User result = this.userRoleService.revokeRole(TARGET_ID, "ADMIN", ADMIN);

		assertThat(result).isEqualTo(updated);
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		AuditEvent event = this.auditEventCaptor.getValue();
		assertThat(event.action()).isEqualTo(AuditAction.ROLE_REVOKED);
		assertThat(event.actorUserId()).isEqualTo(ADMIN.userId());
		assertThat(event.targetUserId()).isEqualTo(TARGET_ID);
		assertThat(event.targetLabel()).isEqualTo("ADMIN");
		assertThat(event.occurredAt()).isEqualTo(NOW);
	}

	@Test
	void rejectsAssigningARoleThatDoesNotExist() {
		when(this.roleDirectoryPort.listRoles()).thenReturn(ROLE_CATALOG);

		assertThatThrownBy(() -> this.userRoleService.assignRole(TARGET_ID, "NAVIGATOR", ADMIN))
			.isInstanceOf(RoleNotFoundException.class);

		verify(this.userDirectoryPort, never()).assignRole(any(), any());
		verifyNoInteractions(this.auditLogPort);
	}

	@Test
	void rejectsRevokingARoleThatDoesNotExist() {
		when(this.roleDirectoryPort.listRoles()).thenReturn(ROLE_CATALOG);

		assertThatThrownBy(() -> this.userRoleService.revokeRole(TARGET_ID, "NAVIGATOR", ADMIN))
			.isInstanceOf(RoleNotFoundException.class);

		verify(this.userDirectoryPort, never()).revokeRole(any(), any());
		verifyNoInteractions(this.auditLogPort);
	}

	private static User targetUser(List<String> roles) {
		return new User(TARGET_ID, "usopp", TARGET_EMAIL, AccountStatus.ACTIVE, roles, NOW);
	}

}
