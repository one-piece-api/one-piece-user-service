package dev.onepieceapi.userservice.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One audit record (§13 of {@code application-user-identity-management.md}) - never
 * carries credential material, only who did what to whom and when.
 */
public record AuditEventResponse(String action, UUID actorUserId, String actorEmail, UUID targetUserId,
		String targetEmail, Instant occurredAt) {

}
