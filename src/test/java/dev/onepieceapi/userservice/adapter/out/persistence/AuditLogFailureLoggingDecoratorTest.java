package dev.onepieceapi.userservice.adapter.out.persistence;

import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogFailureLoggingDecoratorTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T10:00:00Z");

	private static final AuditEvent EVENT = new AuditEvent(AuditAction.ACCESS_REVOKED, UUID.randomUUID(),
			"luffy@onepiece.local", UUID.randomUUID(), "usopp@onepiece.local", OCCURRED_AT);

	@Mock
	private JpaAuditLogAdapter delegate;

	private AuditLogFailureLoggingDecorator decorator;

	@BeforeEach
	void setUp() {
		this.decorator = new AuditLogFailureLoggingDecorator(this.delegate);
	}

	@Test
	void delegatesRecordWhenTheWriteSucceeds() {
		this.decorator.record(EVENT);

		verify(this.delegate).record(EVENT);
	}

	@Test
	void rethrowsWithoutSwallowingWhenTheWriteFails() {
		var failure = new RuntimeException("db unavailable");
		doThrow(failure).when(this.delegate).record(EVENT);

		assertThatThrownBy(() -> this.decorator.record(EVENT)).isSameAs(failure);
	}

	@Test
	void delegatesReadsUnchanged() {
		UUID targetUserId = UUID.randomUUID();
		when(this.delegate.findEvents(0, 10, targetUserId)).thenReturn(List.of(EVENT));
		when(this.delegate.countEvents(targetUserId)).thenReturn(1L);

		assertThat(this.decorator.findEvents(0, 10, targetUserId)).containsExactly(EVENT);
		assertThat(this.decorator.countEvents(targetUserId)).isEqualTo(1L);
	}

}
