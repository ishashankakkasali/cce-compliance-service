package org.openphc.cce.compliance.service;

import org.openphc.cce.compliance.domain.entity.DeadLetterEvent;
import org.openphc.cce.compliance.domain.enums.FailureStage;
import org.openphc.cce.compliance.domain.repository.DeadLetterEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages dead-letter events — recording failed events for later retry or manual resolution.
 */
@Service
@Transactional
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private static final long RETRY_DELAY_MINUTES = 5;

    private final DeadLetterEventRepository deadLetterEventRepository;

    public DeadLetterService(DeadLetterEventRepository deadLetterEventRepository) {
        this.deadLetterEventRepository = deadLetterEventRepository;
    }

    /**
     * Records a dead-letter event.
     *
     * @param payload       the original event payload
     * @param failureReason the failure reason
     * @param failureStage  the stage at which the failure occurred
     * @return the recorded dead-letter event
     */
    @SuppressWarnings("unchecked")
    public DeadLetterEvent recordDeadLetter(Object payload, String failureReason, FailureStage failureStage) {
        DeadLetterEvent event = new DeadLetterEvent();
        if (payload instanceof Map) {
            event.setPayload((Map<String, Object>) payload);
        } else {
            event.setPayload(Map.of("raw", String.valueOf(payload)));
        }
        event.setFailureReason(failureReason);
        event.setFailureStage(failureStage);
        event.setCreatedAt(OffsetDateTime.now());
        event.setRetryCount(0);
        event.setNextRetryAt(OffsetDateTime.now().plusMinutes(RETRY_DELAY_MINUTES));
        event.setResolved(false);

        event = deadLetterEventRepository.save(event);
        log.warn("Recorded dead-letter event: id={}, reason={}, stage={}",
                event.getId(), failureReason, failureStage);
        return event;
    }

    /**
     * Finds unresolved dead-letter events eligible for retry.
     */
    @Transactional(readOnly = true)
    public List<DeadLetterEvent> findRetryableEvents() {
        return deadLetterEventRepository.findRetryableEvents(OffsetDateTime.now());
    }

    /**
     * Marks a dead-letter event as resolved.
     */
    public void resolveDeadLetter(UUID id) {
        deadLetterEventRepository.findById(id).ifPresent(event -> {
            event.setResolved(true);
            event.setResolvedAt(OffsetDateTime.now());
            deadLetterEventRepository.save(event);
            log.info("Resolved dead-letter event: id={}", id);
        });
    }

    /**
     * Increments the retry count and schedules the next retry.
     */
    public void incrementRetry(UUID id) {
        deadLetterEventRepository.findById(id).ifPresent(event -> {
            int newCount = event.getRetryCount() + 1;
            event.setRetryCount(newCount);
            // Exponential backoff: 5, 10, 20, 40, 80 minutes
            long delayMinutes = RETRY_DELAY_MINUTES * (long) Math.pow(2, newCount - 1);
            event.setNextRetryAt(OffsetDateTime.now().plusMinutes(delayMinutes));
            deadLetterEventRepository.save(event);
            log.info("Dead-letter event retry queued: id={}, retryCount={}, nextRetryAt={}",
                    id, newCount, event.getNextRetryAt());
        });
    }

    @Transactional(readOnly = true)
    public List<DeadLetterEvent> findUnresolved() {
        return deadLetterEventRepository.findByResolvedFalse();
    }

    @Transactional(readOnly = true)
    public long countUnresolved() {
        return deadLetterEventRepository.countByResolvedFalse();
    }
}
