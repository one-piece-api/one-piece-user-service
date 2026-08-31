package dev.onepieceapi.userservice.adapter.out.persistence;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

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

	@Override
	public List<AuditEvent> findEvents(int offset, int limit, UUID targetUserId) {
		// offset is always page-aligned here: both callers (AuditQueryService, its
		// tests) derive it from a Pageable's own getOffset(), never an arbitrary value.
		Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "occurredAt"));
		return findEntities(pageable, targetUserId).stream().map(JpaAuditLogAdapter::toDomain).toList();
	}

	private List<AuditLogEntity> findEntities(Pageable pageable, UUID targetUserId) {
		if (targetUserId == null) {
			return this.auditLogRepository.findAll(pageable).getContent();
		}
		return this.auditLogRepository.findByTargetUserId(targetUserId, pageable);
	}

	@Override
	public long countEvents(UUID targetUserId) {
		if (targetUserId == null) {
			return this.auditLogRepository.count();
		}
		return this.auditLogRepository.countByTargetUserId(targetUserId);
	}

	private static AuditEvent toDomain(AuditLogEntity entity) {
		var action = AuditAction.valueOf(entity.getAction());
		return new AuditEvent(action, entity.getActorUserId(), entity.getActorEmail(), entity.getTargetUserId(),
				entity.getTargetEmail(), entity.getOccurredAt());
	}

}
