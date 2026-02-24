package org.openphc.cce.compliance.domain.repository;

import org.openphc.cce.compliance.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEventCategory(String eventCategory, Pageable pageable);

    Page<AuditLog> findByActor(String actor, Pageable pageable);

    Page<AuditLog> findByResourceTypeAndResourceId(String resourceType, String resourceId, Pageable pageable);
}
