package dev.onepieceapi.userservice.adapter.out.persistence;

import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Runs against a real PostgreSQL (Testcontainers), exercising both the Flyway migration
 * (V1__create_audit_log.sql) and the JPA mapping together - see
 * docs/adr/0001-audit-log-persistence.md for why this table exists at all.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
// @DataJpaTest's own auto-configuration list doesn't include Flyway (it normally assumes
// Hibernate generates the schema) - explicitly pulled in here since this table is
// Flyway-managed (V1__create_audit_log.sql) and spring.jpa.hibernate.ddl-auto=validate
// needs it to already exist.
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class JpaAuditLogAdapterTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private AuditLogRepository auditLogRepository;

	private JpaAuditLogAdapter jpaAuditLogAdapter;

	@BeforeEach
	void setUp() {
		this.jpaAuditLogAdapter = new JpaAuditLogAdapter(this.auditLogRepository);
	}

	@Test
	void persistsWhoInvitedWhomAndWhen() {
		var actorUserId = UUID.randomUUID();
		var targetUserId = UUID.randomUUID();
		var occurredAt = Instant.parse("2026-08-23T10:00:00Z");
		var event = new AuditEvent(AuditAction.USER_INVITED, actorUserId, "luffy@onepiece.local", targetUserId,
				"usopp@onepiece.local", null, occurredAt);

		this.jpaAuditLogAdapter.record(event);

		assertThat(this.auditLogRepository.findAll()).singleElement().satisfies(entity -> {
			assertThat(entity.getAction()).isEqualTo("USER_INVITED");
			assertThat(entity.getActorUserId()).isEqualTo(actorUserId);
			assertThat(entity.getActorEmail()).isEqualTo("luffy@onepiece.local");
			assertThat(entity.getTargetUserId()).isEqualTo(targetUserId);
			assertThat(entity.getTargetEmail()).isEqualTo("usopp@onepiece.local");
			assertThat(entity.getTargetLabel()).isNull();
			assertThat(entity.getOccurredAt()).isEqualTo(occurredAt);
		});
	}

	@Test
	void persistsACatalogActionWithNoTargetUser() {
		var actorUserId = UUID.randomUUID();
		var occurredAt = Instant.parse("2026-08-23T10:00:00Z");
		var event = new AuditEvent(AuditAction.ROLE_CREATED, actorUserId, "luffy@onepiece.local", null, null,
				"NAVIGATOR", occurredAt);

		this.jpaAuditLogAdapter.record(event);

		assertThat(this.auditLogRepository.findAll()).singleElement().satisfies(entity -> {
			assertThat(entity.getAction()).isEqualTo("ROLE_CREATED");
			assertThat(entity.getTargetUserId()).isNull();
			assertThat(entity.getTargetEmail()).isNull();
			assertThat(entity.getTargetLabel()).isEqualTo("NAVIGATOR");
		});
	}

	@Test
	void findsEveryEventNewestFirstWhenNoUserIsGiven() {
		var actorId = UUID.randomUUID();
		var targetId = UUID.randomUUID();
		this.jpaAuditLogAdapter.record(eventAt(actorId, targetId, Instant.parse("2026-08-23T10:00:00Z")));
		this.jpaAuditLogAdapter.record(eventAt(actorId, targetId, Instant.parse("2026-08-23T11:00:00Z")));

		List<AuditEvent> events = this.jpaAuditLogAdapter.findEvents(0, 10, null);

		assertThat(events).extracting(AuditEvent::occurredAt)
			.containsExactly(Instant.parse("2026-08-23T11:00:00Z"), Instant.parse("2026-08-23T10:00:00Z"));
		assertThat(this.jpaAuditLogAdapter.countEvents(null)).isEqualTo(2);
	}

	@Test
	void findsOnlyTheGivenTargetUsersEvents() {
		var actorId = UUID.randomUUID();
		var luffyId = UUID.randomUUID();
		var namiId = UUID.randomUUID();
		this.jpaAuditLogAdapter.record(eventAt(actorId, luffyId, Instant.parse("2026-08-23T10:00:00Z")));
		this.jpaAuditLogAdapter.record(eventAt(actorId, namiId, Instant.parse("2026-08-23T11:00:00Z")));

		List<AuditEvent> events = this.jpaAuditLogAdapter.findEvents(0, 10, luffyId);

		assertThat(events).extracting(AuditEvent::targetUserId).containsExactly(luffyId);
		assertThat(this.jpaAuditLogAdapter.countEvents(luffyId)).isEqualTo(1);
	}

	private static AuditEvent eventAt(UUID actorId, UUID targetId, Instant occurredAt) {
		return new AuditEvent(AuditAction.USER_INVITED, actorId, "luffy@onepiece.local", targetId,
				"usopp@onepiece.local", null, occurredAt);
	}

}
