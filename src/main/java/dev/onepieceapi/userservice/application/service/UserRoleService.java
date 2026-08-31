package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.exception.RoleNotFoundException;
import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.application.port.out.RoleDirectoryPort;
import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * UF-IDU-15/16: grants or revokes one realm role on an existing account. Like
 * {@link UserInvitationService}'s resend, this mutates an account that already exists
 * rather than creating one, so an audit-write failure here is logged and rethrown, not
 * compensated for by undoing the role change.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class UserRoleService {

	private final UserDirectoryPort userDirectoryPort;

	private final RoleDirectoryPort roleDirectoryPort;

	private final AuditLogPort auditLogPort;

	private final Clock clock;

	public User assignRole(UUID userId, String role, User actor) {
		requireRoleExists(role);
		User updated = this.userDirectoryPort.assignRole(userId, role);
		this.auditLogPort.record(AuditEventMapper.roleAssigned(actor, updated, Instant.now(this.clock)));
		return updated;
	}

	public User revokeRole(UUID userId, String role, User actor) {
		requireRoleExists(role);
		User updated = this.userDirectoryPort.revokeRole(userId, role);
		this.auditLogPort.record(AuditEventMapper.roleRevoked(actor, updated, Instant.now(this.clock)));
		return updated;
	}

	/**
	 * Roles are dynamic (see
	 * {@code docs/adr/0012-role-permission-catalog-management.md}), so an unrecognized
	 * role name no longer fails fast at deserialization the way the old fixed enum did -
	 * checked explicitly here instead, against the live catalog.
	 */
	private void requireRoleExists(String role) {
		if (!this.roleDirectoryPort.listRoles().containsKey(role)) {
			throw new RoleNotFoundException(role);
		}
	}

}
