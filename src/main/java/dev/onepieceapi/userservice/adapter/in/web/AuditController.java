package dev.onepieceapi.userservice.adapter.in.web;

import dev.onepieceapi.userservice.adapter.in.web.dto.AuditEventResponse;
import dev.onepieceapi.userservice.adapter.in.web.dto.PageResponse;
import dev.onepieceapi.userservice.adapter.in.web.mapper.AuditEventResponseMapper;
import dev.onepieceapi.userservice.application.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The audit read path from Step 17 - {@code GET /audit}, gated by the {@code audit:read}
 * permission authority (see {@code security.SecuredEndpoint}). Kept out of
 * {@link UserController}: it reads from
 * {@link dev.onepieceapi.userservice.application.port.out.AuditLogPort}, not
 * {@code UserDirectoryPort}.
 */
@RestController
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class AuditController {

	private final AuditQueryService auditQueryService;

	@GetMapping(ApiPaths.AUDIT)
	PageResponse<AuditEventResponse> list(Pageable pageable, @RequestParam(required = false) UUID userId) {
		Page<AuditEventResponse> page = this.auditQueryService.list(pageable, userId)
			.map(AuditEventResponseMapper::toResponse);
		return PageResponse.from(page);
	}

}
