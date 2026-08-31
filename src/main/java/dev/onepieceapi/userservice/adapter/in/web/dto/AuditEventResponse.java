package dev.onepieceapi.userservice.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One audit record (§13 of {@code application-user-identity-management.md}) - never
 * carries credential material, only who did what to whom and when. {@code targetUserId}/
 * {@code targetEmail} are set for a user-targeting action; {@code targetLabel} instead,
 * for a role/permission catalog action.
 */
public record AuditEventResponse(String action, UUID actorUserId, String actorEmail, UUID targetUserId,
		String targetEmail, String targetLabel, Instant occurredAt) {

}
