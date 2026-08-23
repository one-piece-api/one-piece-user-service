package dev.onepieceapi.userservice.adapter.out.persistence;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.domain.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Postgres-backed implementation of {@link AuditLogPort} - the only place in this
 * codebase with a datasource of its own (see
 * {@code docs/adr/0001-audit-log-persistence.md}). Every other read/write in this service
 * goes through Keycloak instead.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class JpaAuditLogAdapter implements AuditLogPort {

	private final AuditLogRepository auditLogRepository;

	@Override
	public void record(AuditEvent event) {
		var entity = new AuditLogEntity(event.action().name(), event.actorUserId(), event.actorEmail(),
				event.targetUserId(), event.targetEmail(), event.occurredAt());
		this.auditLogRepository.save(entity);
	}

}
