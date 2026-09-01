package dev.onepieceapi.userservice.adapter.out.persistence;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.AuditLogFilter;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
				event.targetUserId(), event.targetEmail(), event.targetLabel(), event.occurredAt());
		this.auditLogRepository.save(entity);
	}

	@Override
	public List<AuditEvent> findEvents(int offset, int limit, AuditLogFilter filter) {
		// offset is always page-aligned here: both callers (AuditQueryService, its
		// tests) derive it from a Pageable's own getOffset(), never an arbitrary value.
		Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "occurredAt"));
		return this.auditLogRepository.findAll(toSpecification(filter), pageable)
			.map(JpaAuditLogAdapter::toDomain)
			.getContent();
	}

	@Override
	public long countEvents(AuditLogFilter filter) {
		return this.auditLogRepository.count(toSpecification(filter));
	}

	@Override
	public List<String> listDistinctActorEmails() {
		return this.auditLogRepository.findDistinctActorEmails();
	}

	private static Specification<AuditLogEntity> toSpecification(AuditLogFilter filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (filter.targetUserId() != null) {
				predicates.add(cb.equal(root.get("targetUserId"), filter.targetUserId()));
			}
			if (!filter.actions().isEmpty()) {
				List<String> actionNames = filter.actions().stream().map(AuditAction::name).toList();
				predicates.add(root.get("action").in(actionNames));
			}
			if (filter.actorEmail() != null) {
				predicates.add(cb.equal(root.get("actorEmail"), filter.actorEmail()));
			}
			if (filter.occurredFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.occurredFrom()));
			}
			if (filter.occurredToExclusive() != null) {
				predicates.add(cb.lessThan(root.get("occurredAt"), filter.occurredToExclusive()));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private static AuditEvent toDomain(AuditLogEntity entity) {
		var action = AuditAction.valueOf(entity.getAction());
		return new AuditEvent(action, entity.getActorUserId(), entity.getActorEmail(), entity.getTargetUserId(),
				entity.getTargetEmail(), entity.getTargetLabel(), entity.getOccurredAt());
	}

}
