package dev.onepieceapi.userservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {

	@Query("select distinct e.actorEmail from AuditLogEntity e order by e.actorEmail")
	List<String> findDistinctActorEmails();

}
