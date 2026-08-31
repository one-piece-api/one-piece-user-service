package dev.onepieceapi.userservice.adapter.in.web.mapper;

import dev.onepieceapi.userservice.adapter.in.web.dto.AuditEventResponse;
import dev.onepieceapi.userservice.domain.AuditEvent;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AuditEventResponseMapper {

	public AuditEventResponse toResponse(AuditEvent event) {
		return new AuditEventResponse(event.action().name(), event.actorUserId(), event.actorEmail(),
				event.targetUserId(), event.targetEmail(), event.targetLabel(), event.occurredAt());
	}

}
