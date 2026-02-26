package org.openphc.cce.compliance.service;

import org.openphc.cce.compliance.domain.entity.AuditLog;
import org.openphc.cce.compliance.domain.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Audit logging service for compliance operations.
 * <p>
 * Records all significant operations (protocol enrollment, step completion,
 * deviation detection, PlanDefinition loading, etc.) for audit trail purposes.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an audit log entry asynchronously in a new transaction.
     */
    @Async("asyncExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(String eventCategory, String eventType, String actor,
                      String resourceType, String resourceId,
                      Map<String, Object> details, String ipAddress) {
        AuditLog entry = new AuditLog();
        entry.setEventCategory(eventCategory);
        entry.setEventType(eventType);
        entry.setActor(actor);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);
        entry.setDetails(details);
        entry.setIpAddress(ipAddress);
        entry.setTimestamp(OffsetDateTime.now());

        auditLogRepository.save(entry);
        log.debug("Audit log recorded: category={}, type={}, resourceType={}, resourceId={}",
                eventCategory, eventType, resourceType, resourceId);
    }

    /**
     * Convenience method for system-initiated audit events.
     */
    @Async("asyncExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void auditSystem(String eventCategory, String eventType,
                             String resourceType, String resourceId,
                             Map<String, Object> details) {
        audit(eventCategory, eventType, "system", resourceType, resourceId, details, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findByEventCategory(String eventCategory, Pageable pageable) {
        return auditLogRepository.findByEventCategory(eventCategory, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findByActor(String actor, Pageable pageable) {
        return auditLogRepository.findByActor(actor, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findByResourceTypeAndResourceId(String resourceType, String resourceId,
                                                            Pageable pageable) {
        return auditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable);
    }
}
