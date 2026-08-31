package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

	private static final User ADMIN = new User(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "luffy",
			"luffy@onepiece.local", AccountStatus.ACTIVE, List.of("ADMIN"), null);

	private static final UUID TARGET_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

	private static final String TARGET_EMAIL = "usopp@onepiece.local";

	@Mock
	private UserDirectoryPort userDirectoryPort;

	@Mock
	private AuditLogPort auditLogPort;

	@Captor
	private ArgumentCaptor<AuditEvent> auditEventCaptor;

	private UserAccessService userAccessService;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var service = new UserAccessService(this.userDirectoryPort, this.auditLogPort, clock);
		this.userAccessService = service;
	}

	@Test
	void revokesAccessAndRecordsWhoDidIt() {
		var updated = targetUser(AccountStatus.DISABLED);
		when(this.userDirectoryPort.revokeAccess(TARGET_ID)).thenReturn(updated);

		User result = this.userAccessService.revokeAccess(TARGET_ID, ADMIN);

		assertThat(result).isEqualTo(updated);
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		AuditEvent event = this.auditEventCaptor.getValue();
		assertThat(event.action()).isEqualTo(AuditAction.ACCESS_REVOKED);
		assertThat(event.actorUserId()).isEqualTo(ADMIN.userId());
		assertThat(event.targetUserId()).isEqualTo(TARGET_ID);
		assertThat(event.occurredAt()).isEqualTo(NOW);
	}

	@Test
	void reactivatesAndRecordsWhoDidIt() {
		var updated = targetUser(AccountStatus.ACTIVE);
		when(this.userDirectoryPort.reactivate(TARGET_ID)).thenReturn(updated);

		User result = this.userAccessService.reactivate(TARGET_ID, ADMIN);

		assertThat(result).isEqualTo(updated);
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		AuditEvent event = this.auditEventCaptor.getValue();
		assertThat(event.action()).isEqualTo(AuditAction.ACCESS_REACTIVATED);
		assertThat(event.actorUserId()).isEqualTo(ADMIN.userId());
		assertThat(event.targetUserId()).isEqualTo(TARGET_ID);
		assertThat(event.occurredAt()).isEqualTo(NOW);
	}

	private static User targetUser(AccountStatus status) {
		return new User(TARGET_ID, "usopp", TARGET_EMAIL, status, List.of("EDITOR"), NOW);
	}

}
