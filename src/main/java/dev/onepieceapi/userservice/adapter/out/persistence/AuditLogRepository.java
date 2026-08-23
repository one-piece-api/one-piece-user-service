package dev.onepieceapi.userservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

}
