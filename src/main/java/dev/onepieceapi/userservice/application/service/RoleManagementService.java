package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.exception.InvalidRoleNameException;
import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.application.port.out.RoleDirectoryPort;
import dev.onepieceapi.userservice.domain.PermissionDefinition;
import dev.onepieceapi.userservice.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * UF-IDU (role/permission catalog management, see
 * {@code docs/adr/0012-role-permission-catalog-management.md}): creates/deletes roles,
 * creates permissions, and assigns/revokes a permission on a role. Like every other
 * mutating service in this package, an audit-write failure is logged and rethrown, not
 * compensated for by undoing the change that was already made.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class RoleManagementService {

	private final RoleDirectoryPort roleDirectoryPort;

	private final AuditLogPort auditLogPort;

	private final Clock clock;

	/**
	 * {@code copyFromRole} blank/{@code null} creates the role with no permissions.
	 * {@code name} is normalized (uppercase, non-alphanumeric collapsed to {@code _},
	 * leading/trailing {@code _} trimmed) before anything else - the same rule the
	 * reference UI applies client-side, enforced here too since this is also reachable
	 * directly.
	 */
	public Map<String, List<String>> createRole(String name, String copyFromRole, User actor) {
		String normalized = normalize(name);
		var updated = this.roleDirectoryPort.createRole(normalized, blankToEmpty(copyFromRole));
		this.auditLogPort.record(AuditEventMapper.roleCreated(actor, normalized, Instant.now(this.clock)));
		return updated;
	}

	public void deleteRole(String name, User actor) {
		this.roleDirectoryPort.deleteRole(name);
		this.auditLogPort.record(AuditEventMapper.roleDeleted(actor, name, Instant.now(this.clock)));
	}

	public PermissionDefinition createPermission(String key, String description, User actor) {
		var created = this.roleDirectoryPort.createPermission(key, description);
		this.auditLogPort.record(AuditEventMapper.permissionCreated(actor, key, Instant.now(this.clock)));
		return created;
	}

	public void assignPermission(String role, String permissionKey, User actor) {
		this.roleDirectoryPort.assignPermission(role, permissionKey);
		Instant now = Instant.now(this.clock);
		this.auditLogPort.record(AuditEventMapper.permissionAssignedToRole(actor, role, permissionKey, now));
	}

	public void revokePermission(String role, String permissionKey, User actor) {
		this.roleDirectoryPort.revokePermission(role, permissionKey);
		Instant now = Instant.now(this.clock);
		this.auditLogPort.record(AuditEventMapper.permissionRevokedFromRole(actor, role, permissionKey, now));
	}

	public void deletePermission(String key, User actor) {
		this.roleDirectoryPort.deletePermission(key);
		this.auditLogPort.record(AuditEventMapper.permissionDeleted(actor, key, Instant.now(this.clock)));
	}

	private static String normalize(String name) {
		String normalized = name.trim()
			.toUpperCase(Locale.ROOT)
			.replaceAll("[^A-Z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
		if (normalized.isEmpty()) {
			throw new InvalidRoleNameException(name);
		}
		return normalized;
	}

	private static Optional<String> blankToEmpty(String value) {
		return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
	}

}
