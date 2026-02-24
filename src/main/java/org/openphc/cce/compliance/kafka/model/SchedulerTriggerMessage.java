package org.openphc.cce.compliance.kafka.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Scheduler trigger message consumed from cce.scheduler.triggers.
 * Represents a time-based state transition request from the Scheduler Service.
 */
public class SchedulerTriggerMessage {

    private UUID stepInstanceId;
    private String transitionType;
    private OffsetDateTime triggeredAt;
    private String correlationId;

    public SchedulerTriggerMessage() {
    }

    // --- Getters and Setters ---

    public UUID getStepInstanceId() {
        return stepInstanceId;
    }

    public void setStepInstanceId(UUID stepInstanceId) {
        this.stepInstanceId = stepInstanceId;
    }

    public String getTransitionType() {
        return transitionType;
    }

    public void setTransitionType(String transitionType) {
        this.transitionType = transitionType;
    }

    public OffsetDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(OffsetDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
