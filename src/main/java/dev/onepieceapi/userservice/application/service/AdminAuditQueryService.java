package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.AuditLogPort;
import dev.onepieceapi.userservice.domain.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Backs the Step 17 audit read path - the first place {@code audit_log} is ever queried.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AdminAuditQueryService {

	private final AuditLogPort auditLogPort;

	/** {@code targetUserId == null} returns the full trail, newest first. */
	public Page<AuditEvent> list(Pageable pageable, UUID targetUserId) {
		int offset = (int) pageable.getOffset();
		List<AuditEvent> content = this.auditLogPort.findEvents(offset, pageable.getPageSize(), targetUserId);

		return new PageImpl<>(content, pageable, this.auditLogPort.countEvents(targetUserId));
	}

}
