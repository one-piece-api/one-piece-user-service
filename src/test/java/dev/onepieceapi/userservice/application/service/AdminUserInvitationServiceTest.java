package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.RealmRole;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserInvitationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");

	private static final User ADMIN = new User(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
			"luffy@onepiece.local", AccountStatus.ACTIVE, List.of("ADMIN"), null);

	private static final UUID INVITED_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

	private static final String INVITED_EMAIL = "usopp@onepiece.local";

	@Mock
	private UserDirectoryPort userDirectoryPort;

	@Mock
	private AuditLogPort auditLogPort;

	@Captor
	private ArgumentCaptor<AuditEvent> auditEventCaptor;

	private AdminUserInvitationService adminUserInvitationService;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var service = new AdminUserInvitationService(this.userDirectoryPort, this.auditLogPort, clock);
		this.adminUserInvitationService = service;
	}

	@Test
	void invitesTheUserThroughTheIdentityDirectoryAndRecordsWhoDidIt() {
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);
		var invited = new User(INVITED_ID, INVITED_EMAIL, AccountStatus.PENDING, List.of("EDITOR"), NOW);
		when(this.userDirectoryPort.inviteUser(INVITED_EMAIL, roles)).thenReturn(invited);

		User result = this.adminUserInvitationService.invite(INVITED_EMAIL, roles, ADMIN);

		assertThat(result).isEqualTo(invited);
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		AuditEvent event = this.auditEventCaptor.getValue();
		assertThat(event.action()).isEqualTo(AuditAction.USER_INVITED);
		assertThat(event.actorUserId()).isEqualTo(ADMIN.userId());
		assertThat(event.actorEmail()).isEqualTo(ADMIN.email());
		assertThat(event.targetUserId()).isEqualTo(INVITED_ID);
		assertThat(event.targetEmail()).isEqualTo(INVITED_EMAIL);
		assertThat(event.occurredAt()).isEqualTo(NOW);
	}

	@Test
	void doesNotRecordAnAuditEventWhenProvisioningFails() {
		Set<RealmRole> roles = Set.of(RealmRole.ADMIN);
		when(this.userDirectoryPort.inviteUser(INVITED_EMAIL, roles))
			.thenThrow(new RuntimeException("Keycloak unreachable"));

		assertThatThrownBy(() -> this.adminUserInvitationService.invite(INVITED_EMAIL, roles, ADMIN))
			.isInstanceOf(RuntimeException.class);

		verifyNoInteractions(this.auditLogPort);
		verify(this.userDirectoryPort, never()).rollbackInvitation(any());
	}

	@Test
	void rollsBackTheInvitationWhenTheAuditWriteFails() {
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);
		var invited = new User(INVITED_ID, INVITED_EMAIL, AccountStatus.PENDING, List.of("EDITOR"), NOW);
		when(this.userDirectoryPort.inviteUser(INVITED_EMAIL, roles)).thenReturn(invited);
		var auditFailure = new RuntimeException("Postgres unreachable");
		doThrow(auditFailure).when(this.auditLogPort).record(any());

		assertThatThrownBy(() -> this.adminUserInvitationService.invite(INVITED_EMAIL, roles, ADMIN))
			.isSameAs(auditFailure);

		verify(this.userDirectoryPort).rollbackInvitation(INVITED_ID);
	}

	@Test
	void stillPropagatesTheOriginalFailureWhenRollingBackItselfFails() {
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);
		var invited = new User(INVITED_ID, INVITED_EMAIL, AccountStatus.PENDING, List.of("EDITOR"), NOW);
		when(this.userDirectoryPort.inviteUser(INVITED_EMAIL, roles)).thenReturn(invited);
		var auditFailure = new RuntimeException("Postgres unreachable");
		doThrow(auditFailure).when(this.auditLogPort).record(any());
		doThrow(new RuntimeException("Keycloak unreachable")).when(this.userDirectoryPort)
			.rollbackInvitation(INVITED_ID);

		assertThatThrownBy(() -> this.adminUserInvitationService.invite(INVITED_EMAIL, roles, ADMIN))
			.isSameAs(auditFailure);
	}

}
