package dev.onepieceapi.userservice.application.port.out;

import dev.onepieceapi.userservice.domain.AuditEvent;

/**
 * Outbound port for recording audit events (§13 of
 * {@code application-user-identity-management.md}). Kept separate from
 * {@link UserDirectoryPort}: this is application-owned data with no identity-provider
 * equivalent, backed by a persistence adapter rather than an identity-provider one - see
 * {@code docs/adr/0001-audit-log-persistence.md}.
 */
public interface AuditLogPort {

	void record(AuditEvent event);

}
