package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * UF-IDU-13/14: revokes or restores an existing account's access. Like
 * {@link UserRoleService}, this mutates an account that already exists rather than
 * creating one, so an audit-write failure here is logged and rethrown by the caller, not
 * compensated for by undoing the access change.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class UserAccessService {

	private final UserDirectoryPort userDirectoryPort;

	private final AuditLogPort auditLogPort;

	private final Clock clock;

	public User revokeAccess(UUID userId, User actor) {
		User updated = this.userDirectoryPort.revokeAccess(userId);
		this.auditLogPort.record(AuditEventMapper.accessRevoked(actor, updated, Instant.now(this.clock)));
		return updated;
	}

	public User reactivate(UUID userId, User actor) {
		User updated = this.userDirectoryPort.reactivate(userId);
		this.auditLogPort.record(AuditEventMapper.accessReactivated(actor, updated, Instant.now(this.clock)));
		return updated;
	}

}
