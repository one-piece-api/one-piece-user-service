package dev.onepieceapi.userservice.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

	List<AuditLogEntity> findByTargetUserId(UUID targetUserId, Pageable pageable);

	long countByTargetUserId(UUID targetUserId);

}
