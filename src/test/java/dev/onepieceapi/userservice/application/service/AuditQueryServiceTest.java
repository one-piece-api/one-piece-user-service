package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.AuditLogFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

	private static final UUID ACTOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	private static final UUID TARGET_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

	@Mock
	private AuditLogPort auditLogPort;

	private AuditQueryService auditQueryService;

	@BeforeEach
	void setUp() {
		this.auditQueryService = new AuditQueryService(this.auditLogPort);
	}

	@Test
	void listsTheFullTrailWhenNoFilterIsGiven() {
		Pageable pageable = PageRequest.of(0, 20);
		var event = anEvent();
		var filter = AuditLogFilter.of(null, null, null, null, null);
		when(this.auditLogPort.findEvents(0, 20, filter)).thenReturn(List.of(event));
		when(this.auditLogPort.countEvents(filter)).thenReturn(1L);

		Page<AuditEvent> page = this.auditQueryService.list(pageable, null, null, null, null, null);

		assertThat(page.getContent()).containsExactly(event);
	}

	@Test
	void reportsTheFullEventCountRatherThanThisPagesSize() {
		Pageable pageable = PageRequest.of(0, 1);
		var event = anEvent();
		var filter = AuditLogFilter.of(null, null, null, null, null);
		when(this.auditLogPort.findEvents(0, 1, filter)).thenReturn(List.of(event));
		when(this.auditLogPort.countEvents(filter)).thenReturn(37L);

		Page<AuditEvent> page = this.auditQueryService.list(pageable, null, null, null, null, null);

		assertThat(page.getTotalElements()).isEqualTo(37L);
		assertThat(page.getContent()).hasSize(1);
	}

	@Test
	void passesTheTargetUserIdThroughToThePort() {
		Pageable pageable = PageRequest.of(0, 20);
		var event = anEvent();
		var filter = AuditLogFilter.of(TARGET_ID, null, null, null, null);
		when(this.auditLogPort.findEvents(0, 20, filter)).thenReturn(List.of(event));
		when(this.auditLogPort.countEvents(filter)).thenReturn(1L);

		Page<AuditEvent> page = this.auditQueryService.list(pageable, TARGET_ID, null, null, null, null);

		assertThat(page.getContent()).containsExactly(event);
	}

	@Test
	void buildsACombinedFilterFromEveryOptionalParameter() {
		Pageable pageable = PageRequest.of(0, 20);
		var event = anEvent();
		var actions = Set.of(AuditAction.ROLE_ASSIGNED);
		var from = LocalDate.of(2026, 8, 1);
		var to = LocalDate.of(2026, 8, 31);
		var filter = AuditLogFilter.of(null, actions, "luffy@onepiece.local", from, to);
		when(this.auditLogPort.findEvents(0, 20, filter)).thenReturn(List.of(event));
		when(this.auditLogPort.countEvents(filter)).thenReturn(1L);

		Page<AuditEvent> page = this.auditQueryService.list(pageable, null, actions, "luffy@onepiece.local", from,
				to);

		assertThat(page.getContent()).containsExactly(event);
	}

	@Test
	void listsEveryDistinctActorForTheAuthorFilter() {
		when(this.auditLogPort.listDistinctActorEmails())
			.thenReturn(List.of("luffy@onepiece.local", "nami@onepiece.local"));

		assertThat(this.auditQueryService.listActors()).containsExactly("luffy@onepiece.local",
				"nami@onepiece.local");
	}

	private static AuditEvent anEvent() {
		return new AuditEvent(AuditAction.USER_INVITED, ACTOR_ID, "luffy@onepiece.local", TARGET_ID,
				"usopp@onepiece.local", null, Instant.parse("2026-08-23T10:00:00Z"));
	}

}
