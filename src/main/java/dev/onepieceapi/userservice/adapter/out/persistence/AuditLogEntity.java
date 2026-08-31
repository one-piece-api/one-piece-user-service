package dev.onepieceapi.userservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The JPA row behind one {@code AuditEvent} - see
 * {@code docs/adr/0001-audit-log-persistence.md} for why this table exists.
 * Package-private: nothing outside {@code JpaAuditLogAdapter} touches this class, only
 * the domain-level {@code AuditEvent} it is mapped from.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor
class AuditLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String action;

	@Column(nullable = false)
	private UUID actorUserId;

	@Column(nullable = false)
	private String actorEmail;

	private UUID targetUserId;

	private String targetEmail;

	private String targetLabel;

	@Column(nullable = false)
	private Instant occurredAt;

	AuditLogEntity(String action, UUID actorUserId, String actorEmail, UUID targetUserId, String targetEmail,
			String targetLabel, Instant occurredAt) {
		this.action = action;
		this.actorUserId = actorUserId;
		this.actorEmail = actorEmail;
		this.targetUserId = targetUserId;
		this.targetEmail = targetEmail;
		this.targetLabel = targetLabel;
		this.occurredAt = occurredAt;
	}

}
