package org.openphc.cce.compliance.service;

import org.openphc.cce.compliance.domain.entity.EventLog;
import org.openphc.cce.compliance.domain.enums.ProcessingStatus;
import org.openphc.cce.compliance.domain.repository.EventLogRepository;
import org.openphc.cce.compliance.kafka.model.CloudEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the event log — recording inbound CloudEvents and their processing outcomes.
 * Handles idempotency checking via (cloudevents_id, source) uniqueness.
 */
@Service
@Transactional
public class EventLogService {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(EventLogService.class);

    private final EventLogRepository eventLogRepository;

    public EventLogService(EventLogRepository eventLogRepository) {
        this.eventLogRepository = eventLogRepository;
    }

    /**
     * Checks whether an event has already been logged (idempotency guard).
     *
     * @param cloudeventsId the CloudEvents ID
     * @param source        the CloudEvents source
     * @return true if the event already exists
     */
    @Transactional(readOnly = true)
    public boolean isDuplicate(String cloudeventsId, String source) {
        return eventLogRepository.existsByCloudeventsIdAndSource(cloudeventsId, source);
    }

    /**
     * Records an inbound CloudEvent in the event log.
     *
     * @param event            the CloudEvent message
     * @param processingStatus the processing outcome
     * @return the created EventLog entry
     */
    public EventLog recordEvent(CloudEventMessage event, ProcessingStatus processingStatus) {
        EventLog entry = new EventLog();
        entry.setCloudeventsId(event.getId());
        entry.setSource(event.getSource());
        entry.setSourceEventId(event.getSourceEventId());
        entry.setSubject(event.getSubject());
        entry.setType(event.getType());
        entry.setEventTime(event.getTime() != null ? event.getTime() : OffsetDateTime.now());
        entry.setReceivedAt(OffsetDateTime.now());
        entry.setCorrelationId(event.getCorrelationId());
        entry.setData(event.getData() != null ? event.getData() : Map.of());
        entry.setFacilityId(event.getFacilityId());
        entry.setProcessingStatus(processingStatus);

        return eventLogRepository.save(entry);
    }

    /**
     * Updates an event log entry with matching results.
     *
     * @param eventLogId          the event log entry ID
     * @param protocolInstanceId  the matched protocol instance ID
     * @param protocolDefinitionId the matched protocol definition ID
     * @param actionId            the matched action ID
     * @param matchedStepId       the matched step instance ID
     * @param status              the updated processing status
     */
    public void updateMatchResult(UUID eventLogId, UUID protocolInstanceId,
                                   UUID protocolDefinitionId, String actionId,
                                   UUID matchedStepId, ProcessingStatus status) {
        eventLogRepository.findById(eventLogId).ifPresent(entry -> {
            entry.setProtocolInstanceId(protocolInstanceId);
            entry.setProtocolDefinitionId(protocolDefinitionId);
            entry.setActionId(actionId);
            entry.setMatchedStepInstanceId(matchedStepId);
            entry.setProcessingStatus(status);
            eventLogRepository.save(entry);
        });
    }

    @Transactional(readOnly = true)
    public Optional<EventLog> findById(UUID id) {
        return eventLogRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<EventLog> findBySubject(String subject) {
        return eventLogRepository.findBySubject(subject);
    }

    @Transactional(readOnly = true)
    public Page<EventLog> findBySubject(String subject, Pageable pageable) {
        return eventLogRepository.findBySubject(subject, pageable);
    }

    @Transactional(readOnly = true)
    public Page<EventLog> findByFacilityId(String facilityId, Pageable pageable) {
        return eventLogRepository.findByFacilityId(facilityId, pageable);
    }
}
