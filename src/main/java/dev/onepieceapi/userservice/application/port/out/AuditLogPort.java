package dev.onepieceapi.userservice.application.port.out;

import dev.onepieceapi.userservice.domain.AuditEvent;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for recording and reading audit events (§13 of
 * {@code application-user-identity-management.md}). Kept separate from
 * {@link UserDirectoryPort}: this is application-owned data with no identity-provider
 * equivalent, backed by a persistence adapter rather than an identity-provider one - see
 * {@code docs/adr/0001-audit-log-persistence.md}.
 */
public interface AuditLogPort {

	void record(AuditEvent event);

	/**
	 * Newest-first. Mirrors {@link UserDirectoryPort#findUsers}'s offset/limit shape
	 * (kept consistent across both ports rather than switching to Spring Data's
	 * {@code Pageable} here just because the underlying store happens to be JPA) -
	 * {@code targetUserId ==
	 * null} returns every record (Step 17), otherwise only the ones for that user.
	 */
	List<AuditEvent> findEvents(int offset, int limit, UUID targetUserId);

	long countEvents(UUID targetUserId);

}
