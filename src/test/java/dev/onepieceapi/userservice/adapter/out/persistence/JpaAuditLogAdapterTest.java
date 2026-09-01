package dev.onepieceapi.userservice.adapter.out.persistence;

import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.AuditLogFilter;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
	void findsEveryEventNewestFirstWhenNoFilterIsGiven() {
		var actorId = UUID.randomUUID();
		var targetId = UUID.randomUUID();
		this.jpaAuditLogAdapter.record(eventAt(actorId, targetId, Instant.parse("2026-08-23T10:00:00Z")));
		this.jpaAuditLogAdapter.record(eventAt(actorId, targetId, Instant.parse("2026-08-23T11:00:00Z")));

		var filter = AuditLogFilter.of(null, null, null, null, null);
		List<AuditEvent> events = this.jpaAuditLogAdapter.findEvents(0, 10, filter);

		assertThat(events).extracting(AuditEvent::occurredAt)
			.containsExactly(Instant.parse("2026-08-23T11:00:00Z"), Instant.parse("2026-08-23T10:00:00Z"));
		assertThat(this.jpaAuditLogAdapter.countEvents(filter)).isEqualTo(2);
	}

	@Test
	void findsOnlyTheGivenTargetUsersEvents() {
		var actorId = UUID.randomUUID();
		var luffyId = UUID.randomUUID();
		var namiId = UUID.randomUUID();
		this.jpaAuditLogAdapter.record(eventAt(actorId, luffyId, Instant.parse("2026-08-23T10:00:00Z")));
		this.jpaAuditLogAdapter.record(eventAt(actorId, namiId, Instant.parse("2026-08-23T11:00:00Z")));

		var filter = AuditLogFilter.forTargetUser(luffyId);
		List<AuditEvent> events = this.jpaAuditLogAdapter.findEvents(0, 10, filter);

		assertThat(events).extracting(AuditEvent::targetUserId).containsExactly(luffyId);
		assertThat(this.jpaAuditLogAdapter.countEvents(filter)).isEqualTo(1);
	}

	@Test
	void findsOnlyEventsMatchingOneOfTheGivenActions() {
		var actorId = UUID.randomUUID();
		var targetId = UUID.randomUUID();
		var roleAssignedAt = Instant.parse("2026-08-23T10:00:00Z");
		var roleAssigned = new AuditEvent(AuditAction.ROLE_ASSIGNED, actorId, "luffy@onepiece.local", targetId,
				"usopp@onepiece.local", "NAVIGATOR", roleAssignedAt);
		this.jpaAuditLogAdapter.record(roleAssigned);
		this.jpaAuditLogAdapter.record(eventAt(actorId, targetId, Instant.parse("2026-08-23T11:00:00Z")));

		var filter = AuditLogFilter.of(null, Set.of(AuditAction.ROLE_ASSIGNED), null, null, null);
		List<AuditEvent> events = this.jpaAuditLogAdapter.findEvents(0, 10, filter);

		assertThat(events).extracting(AuditEvent::action).containsExactly(AuditAction.ROLE_ASSIGNED);
		assertThat(this.jpaAuditLogAdapter.countEvents(filter)).isEqualTo(1);
	}

	@Test
	void findsOnlyEventsFromTheGivenActor() {
		var actorId = UUID.randomUUID();
		var targetId = UUID.randomUUID();
		this.jpaAuditLogAdapter.record(new AuditEvent(AuditAction.USER_INVITED, actorId, "luffy@onepiece.local",
				targetId, "usopp@onepiece.local", null, Instant.parse("2026-08-23T10:00:00Z")));
		this.jpaAuditLogAdapter.record(new AuditEvent(AuditAction.USER_INVITED, actorId, "nami@onepiece.local",
				targetId, "usopp@onepiece.local", null, Instant.parse("2026-08-23T11:00:00Z")));

		var filter = AuditLogFilter.of(null, null, "nami@onepiece.local", null, null);
		List<AuditEvent> events = this.jpaAuditLogAdapter.findEvents(0, 10, filter);

		assertThat(events).extracting(AuditEvent::actorEmail).containsExactly("nami@onepiece.local");
		assertThat(this.jpaAuditLogAdapter.countEvents(filter)).isEqualTo(1);
	}

	@Test
	void findsOnlyEventsWithinTheGivenDateRangeInclusiveOfBothEnds() {
		var actorId = UUID.randomUUID();
		var targetId = UUID.randomUUID();
		this.jpaAuditLogAdapter.record(eventAt(actorId, targetId, Instant.parse("2026-08-22T23:59:59Z")));
		this.jpaAuditLogAdapter.record(eventAt(actorId, targetId, Instant.parse("2026-08-23T10:00:00Z")));
		this.jpaAuditLogAdapter.record(eventAt(actorId, targetId, Instant.parse("2026-08-24T23:59:59Z")));
		this.jpaAuditLogAdapter.record(eventAt(actorId, targetId, Instant.parse("2026-08-25T00:00:00Z")));

		var filter = AuditLogFilter.of(null, null, null, LocalDate.of(2026, 8, 23), LocalDate.of(2026, 8, 24));
		List<AuditEvent> events = this.jpaAuditLogAdapter.findEvents(0, 10, filter);

		var withinRange = List.of(Instant.parse("2026-08-23T10:00:00Z"), Instant.parse("2026-08-24T23:59:59Z"));
		assertThat(events).extracting(AuditEvent::occurredAt).containsExactlyInAnyOrderElementsOf(withinRange);
		assertThat(this.jpaAuditLogAdapter.countEvents(filter)).isEqualTo(2);
	}

	@Test
	void listsEveryDistinctActorSortedAlphabetically() {
		var actorId = UUID.randomUUID();
		var targetId = UUID.randomUUID();
		this.jpaAuditLogAdapter.record(new AuditEvent(AuditAction.USER_INVITED, actorId, "zoro@onepiece.local",
				targetId, "usopp@onepiece.local", null, Instant.parse("2026-08-23T10:00:00Z")));
		this.jpaAuditLogAdapter.record(new AuditEvent(AuditAction.USER_INVITED, actorId, "luffy@onepiece.local",
				targetId, "usopp@onepiece.local", null, Instant.parse("2026-08-23T11:00:00Z")));
		this.jpaAuditLogAdapter.record(new AuditEvent(AuditAction.USER_INVITED, actorId, "luffy@onepiece.local",
				targetId, "usopp@onepiece.local", null, Instant.parse("2026-08-23T12:00:00Z")));

		assertThat(this.jpaAuditLogAdapter.listDistinctActorEmails()).containsExactly("luffy@onepiece.local",
				"zoro@onepiece.local");
	}

	private static AuditEvent eventAt(UUID actorId, UUID targetId, Instant occurredAt) {
		return new AuditEvent(AuditAction.USER_INVITED, actorId, "luffy@onepiece.local", targetId,
				"usopp@onepiece.local", null, occurredAt);
	}

}
