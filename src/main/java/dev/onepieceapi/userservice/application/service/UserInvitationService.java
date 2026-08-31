package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * UF-IDU-01: invites a new application user. Provisioning the identity and recording the
 * audit trail are two different outbound ports ({@link UserDirectoryPort},
 * {@link AuditLogPort}) - this service is the only place that knows both need to happen
 * for one invitation. Not wrapped in a single transaction: Keycloak provisioning isn't a
 * participant in the local database's transaction, so there is no atomicity to buy - the
 * identity provider call happens first (its own single source of truth for "does this
 * user exist"), and only once it succeeds is the audit record written. If that write
 * fails, the invitation is rolled back ({@link UserDirectoryPort#rollbackInvitation}) so
 * the operation is all-or-nothing from the caller's perspective, rather than leaving a
 * real invitation with no audit trail.
 * <p>
 * Also handles resending an invitation (UF-IDU-03): unlike a fresh invite, there is
 * nothing to compensate if the audit write fails - the account already existed and the
 * email was already re-sent, so a failure here is logged and rethrown, not rolled back.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
@Slf4j
public class UserInvitationService {

	private final UserDirectoryPort userDirectoryPort;

	private final AuditLogPort auditLogPort;

	private final Clock clock;

	public User invite(String email, Set<RealmRole> roles, User actor) {
		User invited = this.userDirectoryPort.inviteUser(email, roles);

		try {
			this.auditLogPort.record(AuditEventMapper.userInvited(actor, invited, Instant.now(this.clock)));
		}
		catch (RuntimeException ex) {
			rollBackInvitation(invited, ex);
			throw ex;
		}

		return invited;
	}

	public User resend(UUID userId, User actor) {
		User target = this.userDirectoryPort.resendInvitation(userId);
		this.auditLogPort.record(AuditEventMapper.invitationResent(actor, target, Instant.now(this.clock)));
		return target;
	}

	private void rollBackInvitation(User invited, RuntimeException cause) {
		try {
			this.userDirectoryPort.rollbackInvitation(invited.userId());
		}
		catch (RuntimeException cleanupFailure) {
			String message = "Failed to roll back invitation for {} - manual cleanup needed";
			log.error(message, invited.userId(), cleanupFailure);
			return;
		}
		log.warn("Rolled back invitation for {} after audit write failure", invited.userId(), cause);
	}

}
