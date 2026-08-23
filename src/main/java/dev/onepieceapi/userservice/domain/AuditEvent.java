package dev.onepieceapi.userservice.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A record of who did what to whom, for traceability only (§13 of
 * {@code application-user-identity-management.md}) - never read back by this application,
 * in particular never used to power the admin UI (see {@code AuditLogPort}). Keycloak has
 * no equivalent bookkeeping (its action tokens carry no "who invited whom"), so this is
 * genuinely application-owned data, unlike everything else in this domain.
 */
public record AuditEvent(AuditAction action, UUID actorUserId, String actorEmail, UUID targetUserId, String targetEmail,
		Instant occurredAt) {
}
