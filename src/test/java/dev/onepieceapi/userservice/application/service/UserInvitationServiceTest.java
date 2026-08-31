package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.exception.RoleNotFoundException;
import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.application.port.out.RoleDirectoryPort;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.User;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserInvitationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");

	private static final User ADMIN = new User(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "luffy",
			"luffy@onepiece.local", AccountStatus.ACTIVE, List.of("ADMIN"), null);

	private static final UUID INVITED_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

	private static final String INVITED_EMAIL = "usopp@onepiece.local";

	private static final List<String> INVITED_ROLES = List.of("EDITOR");

	private static final Map<String, List<String>> ROLE_CATALOG = Map.of("ADMIN", List.of(), "EDITOR", List.of());

	@Mock
	private UserDirectoryPort userDirectoryPort;

	@Mock
	private RoleDirectoryPort roleDirectoryPort;

	@Mock
	private AuditLogPort auditLogPort;

	@Captor
	private ArgumentCaptor<AuditEvent> auditEventCaptor;

	private UserInvitationService userInvitationService;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var directory = this.userDirectoryPort;
		var service = new UserInvitationService(directory, this.roleDirectoryPort, this.auditLogPort, clock);
		this.userInvitationService = service;
	}

	@Test
	void invitesTheUserThroughTheIdentityDirectoryAndRecordsWhoDidIt() {
		when(this.roleDirectoryPort.listRoles()).thenReturn(ROLE_CATALOG);
		Set<String> roles = Set.of("EDITOR");
		var invited = pendingInvitedUser();
		when(this.userDirectoryPort.inviteUser(INVITED_EMAIL, roles)).thenReturn(invited);

		User result = this.userInvitationService.invite(INVITED_EMAIL, roles, ADMIN);

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
	void rejectsInvitingWithARoleThatDoesNotExist() {
		when(this.roleDirectoryPort.listRoles()).thenReturn(ROLE_CATALOG);
		Set<String> roles = Set.of("NAVIGATOR");

		assertThatThrownBy(() -> this.userInvitationService.invite(INVITED_EMAIL, roles, ADMIN))
			.isInstanceOf(RoleNotFoundException.class);

		verifyNoInteractions(this.userDirectoryPort);
		verifyNoInteractions(this.auditLogPort);
	}

	@Test
	void doesNotRecordAnAuditEventWhenProvisioningFails() {
		when(this.roleDirectoryPort.listRoles()).thenReturn(ROLE_CATALOG);
		Set<String> roles = Set.of("ADMIN");
		when(this.userDirectoryPort.inviteUser(INVITED_EMAIL, roles))
			.thenThrow(new RuntimeException("Keycloak unreachable"));

		assertThatThrownBy(() -> this.userInvitationService.invite(INVITED_EMAIL, roles, ADMIN))
			.isInstanceOf(RuntimeException.class);

		verifyNoInteractions(this.auditLogPort);
	}

	@Test
	void propagatesTheFailureWhenTheAuditWriteFails() {
		when(this.roleDirectoryPort.listRoles()).thenReturn(ROLE_CATALOG);
		Set<String> roles = Set.of("EDITOR");
		var invited = pendingInvitedUser();
		when(this.userDirectoryPort.inviteUser(INVITED_EMAIL, roles)).thenReturn(invited);
		var auditFailure = new RuntimeException("Postgres unreachable");
		doThrow(auditFailure).when(this.auditLogPort).record(any());
		ThrowingCallable invite = () -> this.userInvitationService.invite(INVITED_EMAIL, roles, ADMIN);

		assertThatThrownBy(invite).isSameAs(auditFailure);
	}

	@Test
	void resendsTheInvitationThroughTheIdentityDirectoryAndRecordsWhoDidIt() {
		var pending = pendingInvitedUser();
		when(this.userDirectoryPort.resendInvitation(INVITED_ID)).thenReturn(pending);

		User result = this.userInvitationService.resend(INVITED_ID, ADMIN);

		assertThat(result).isEqualTo(pending);
		verify(this.auditLogPort).record(this.auditEventCaptor.capture());
		AuditEvent event = this.auditEventCaptor.getValue();
		assertThat(event.action()).isEqualTo(AuditAction.INVITATION_RESENT);
		assertThat(event.actorUserId()).isEqualTo(ADMIN.userId());
		assertThat(event.targetUserId()).isEqualTo(INVITED_ID);
		assertThat(event.occurredAt()).isEqualTo(NOW);
	}

	@Test
	void doesNotRecordAnAuditEventWhenResendFails() {
		when(this.userDirectoryPort.resendInvitation(INVITED_ID))
			.thenThrow(new RuntimeException("Keycloak unreachable"));

		assertThatThrownBy(() -> this.userInvitationService.resend(INVITED_ID, ADMIN))
			.isInstanceOf(RuntimeException.class);

		verifyNoInteractions(this.auditLogPort);
	}

	private static User pendingInvitedUser() {
		return new User(INVITED_ID, INVITED_EMAIL, INVITED_EMAIL, AccountStatus.PENDING, INVITED_ROLES, NOW);
	}

}
