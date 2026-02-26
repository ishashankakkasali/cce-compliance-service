package org.openphc.cce.compliance.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.openphc.cce.compliance.domain.enums.ProcessingStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event log — stores all incoming CloudEvents with their processing results.
 * Partitioned by received_at in PostgreSQL (monthly partitions).
 */
@Entity
@Table(name = "event_log", indexes = {
        @Index(name = "idx_event_log_subject", columnList = "subject"),
        @Index(name = "idx_event_log_facility", columnList = "facility_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cloudevents_id", "source", "received_at"})
})
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cloudevents_id", nullable = false)
    private String cloudeventsId;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_event_id")
    private String sourceEventId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String type;

    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Type(JsonType.class)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> data;

    @Column(name = "protocol_instance_id")
    private UUID protocolInstanceId;

    @Column(name = "protocol_definition_id")
    private UUID protocolDefinitionId;

    @Column(name = "action_id")
    private String actionId;

    @Column(name = "facility_id")
    private String facilityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private ProcessingStatus processingStatus;

    @Column(name = "matched_step_instance_id")
    private UUID matchedStepInstanceId;

    public EventLog() {
    }

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = OffsetDateTime.now();
        }
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCloudeventsId() {
        return cloudeventsId;
    }

    public void setCloudeventsId(String cloudeventsId) {
        this.cloudeventsId = cloudeventsId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public OffsetDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(OffsetDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public UUID getProtocolInstanceId() {
        return protocolInstanceId;
    }

    public void setProtocolInstanceId(UUID protocolInstanceId) {
        this.protocolInstanceId = protocolInstanceId;
    }

    public UUID getProtocolDefinitionId() {
        return protocolDefinitionId;
    }

    public void setProtocolDefinitionId(UUID protocolDefinitionId) {
        this.protocolDefinitionId = protocolDefinitionId;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public UUID getMatchedStepInstanceId() {
        return matchedStepInstanceId;
    }

    public void setMatchedStepInstanceId(UUID matchedStepInstanceId) {
        this.matchedStepInstanceId = matchedStepInstanceId;
    }
}
