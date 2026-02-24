package org.openphc.cce.compliance.kafka.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Intelligence trigger event produced by the Compliance Service
 * when a deviation is detected. Published to cce.intelligence.triggers.
 */
public class IntelligenceTriggerEvent {

    private UUID id;
    private String type;
    private String subject;
    private UUID protocolInstanceId;
    private UUID stepInstanceId;
    private UUID deviationId;
    private String deviationType;
    private String stepState;
    private String actionId;
    private String protocolCanonical;
    private String facilityId;
    private OffsetDateTime detectedAt;
    private Map<String, Object> metadata;

    public IntelligenceTriggerEvent() {
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public UUID getProtocolInstanceId() {
        return protocolInstanceId;
    }

    public void setProtocolInstanceId(UUID protocolInstanceId) {
        this.protocolInstanceId = protocolInstanceId;
    }

    public UUID getStepInstanceId() {
        return stepInstanceId;
    }

    public void setStepInstanceId(UUID stepInstanceId) {
        this.stepInstanceId = stepInstanceId;
    }

    public UUID getDeviationId() {
        return deviationId;
    }

    public void setDeviationId(UUID deviationId) {
        this.deviationId = deviationId;
    }

    public String getDeviationType() {
        return deviationType;
    }

    public void setDeviationType(String deviationType) {
        this.deviationType = deviationType;
    }

    public String getStepState() {
        return stepState;
    }

    public void setStepState(String stepState) {
        this.stepState = stepState;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public String getProtocolCanonical() {
        return protocolCanonical;
    }

    public void setProtocolCanonical(String protocolCanonical) {
        this.protocolCanonical = protocolCanonical;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(OffsetDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
