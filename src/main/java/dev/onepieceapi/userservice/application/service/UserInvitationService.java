package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import lombok.RequiredArgsConstructor;
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
 * user exist"), and only once it succeeds is the audit record written. Like every other
 * mutating service in this package, an audit-write failure here is logged and rethrown by
 * the caller, not compensated for by undoing the change that was already made.
 * <p>
 * Also handles resending an invitation (UF-IDU-03), which follows the same pattern.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class UserInvitationService {

	private final UserDirectoryPort userDirectoryPort;

	private final AuditLogPort auditLogPort;

	private final Clock clock;

	public User invite(String email, Set<RealmRole> roles, User actor) {
		User invited = this.userDirectoryPort.inviteUser(email, roles);
		this.auditLogPort.record(AuditEventMapper.userInvited(actor, invited, Instant.now(this.clock)));
		return invited;
	}

	public User resend(UUID userId, User actor) {
		User target = this.userDirectoryPort.resendInvitation(userId);
		this.auditLogPort.record(AuditEventMapper.invitationResent(actor, target, Instant.now(this.clock)));
		return target;
	}

}
