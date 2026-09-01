package dev.onepieceapi.userservice.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/**
 * The combinable read-side filters for {@code GET /audit} - every field is independently
 * optional (null/empty means "don't filter on this") and all given fields are ANDed
 * together, matching the Ship's Log page's cumulable filter chips.
 * <p>
 * {@code occurredFrom}/{@code occurredToExclusive} are calendar-day boundaries already
 * resolved to instants in UTC, the same zone {@code ClockConfig} timestamps every audit
 * event in - so a "from 23 Aug to 23 Aug" selection means the whole UTC day, not a
 * viewer-local one.
 */
public record AuditLogFilter(UUID targetUserId, Set<AuditAction> actions, String actorEmail, Instant occurredFrom,
		Instant occurredToExclusive) {

	public static AuditLogFilter of(UUID targetUserId, Set<AuditAction> actions, String actorEmail, LocalDate from,
			LocalDate to) {
		Instant occurredFrom = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant occurredToExclusive = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		return new AuditLogFilter(targetUserId, actions == null ? Set.of() : actions, actorEmail, occurredFrom,
				occurredToExclusive);
	}

	public static AuditLogFilter forTargetUser(UUID targetUserId) {
		return new AuditLogFilter(targetUserId, Set.of(), null, null, null);
	}

}
