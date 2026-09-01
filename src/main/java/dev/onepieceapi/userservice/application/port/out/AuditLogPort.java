package dev.onepieceapi.userservice.application.port.out;

import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.AuditLogFilter;

import java.util.List;

/**
 * Outbound port for recording and reading audit events (§13 of
 * {@code application-user-identity-management.md}). Kept separate from
 * {@link UserDirectoryPort}: this is application-owned data with no identity-provider
 * equivalent, backed by a persistence adapter rather than an identity-provider one - see
 * {@code docs/adr/0001-audit-log-persistence.md}.
 */
public interface AuditLogPort {

	void record(AuditEvent event);

	/** Newest-first, every {@link AuditLogFilter} field ANDed together. */
	List<AuditEvent> findEvents(int offset, int limit, AuditLogFilter filter);

	long countEvents(AuditLogFilter filter);

	/**
	 * Every actor who has ever recorded an event, sorted, for the Ship's Log author
	 * filter.
	 */
	List<String> listDistinctActorEmails();

}
