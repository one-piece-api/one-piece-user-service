package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.AuditLogFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Backs the Step 17 audit read path - the first place {@code audit_log} is ever queried.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AuditQueryService {

	private final AuditLogPort auditLogPort;

	/**
	 * {@code targetUserId == null} returns the full trail, newest first; every other
	 * parameter is an optional, cumulable filter (see {@link AuditLogFilter}) for the
	 * Ship's Log page.
	 */
	public Page<AuditEvent> list(Pageable pageable, UUID targetUserId, Set<AuditAction> actions, String actorEmail,
			LocalDate from, LocalDate to) {
		int offset = (int) pageable.getOffset();
		var filter = AuditLogFilter.of(targetUserId, actions, actorEmail, from, to);
		List<AuditEvent> content = this.auditLogPort.findEvents(offset, pageable.getPageSize(), filter);

		return new PageImpl<>(content, pageable, this.auditLogPort.countEvents(filter));
	}

	/**
	 * Every actor who has ever recorded an event, sorted - powers the author filter
	 * dropdown.
	 */
	public List<String> listActors() {
		return this.auditLogPort.listDistinctActorEmails();
	}

}
