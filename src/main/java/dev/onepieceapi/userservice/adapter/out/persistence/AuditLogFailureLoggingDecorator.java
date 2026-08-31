package dev.onepieceapi.userservice.adapter.out.persistence;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.domain.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Decorates {@link JpaAuditLogAdapter} so every {@link AuditLogPort} caller gets the same
 * failure-observability behavior on {@link #record} without each service repeating its
 * own try/catch/log: by the time a service calls {@code record}, the Keycloak operation
 * it is auditing has already succeeded and cannot be rolled back from here, so the only
 * useful thing this decorator can do on failure is make the resulting audit gap loud (an
 * ERROR log carrying the full event) before rethrowing, so it surfaces in monitoring
 * instead of disappearing into a generic 500. {@code @Primary} so this - not the plain
 * JPA adapter - is what gets injected wherever the application depends on
 * {@link AuditLogPort}.
 */
@Component
@Primary
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
@Slf4j
public class AuditLogFailureLoggingDecorator implements AuditLogPort {

	private final JpaAuditLogAdapter delegate;

	@Override
	public void record(AuditEvent event) {
		try {
			this.delegate.record(event);
		}
		catch (RuntimeException ex) {
			String message = "Failed to record audit event {} (actor={}, target={}) - already-successful "
					+ "operation will not be rolled back";
			log.error(message, event.action(), event.actorUserId(), event.targetUserId(), ex);
			throw ex;
		}
	}

	@Override
	public List<AuditEvent> findEvents(int offset, int limit, UUID targetUserId) {
		return this.delegate.findEvents(offset, limit, targetUserId);
	}

	@Override
	public long countEvents(UUID targetUserId) {
		return this.delegate.countEvents(targetUserId);
	}

}
