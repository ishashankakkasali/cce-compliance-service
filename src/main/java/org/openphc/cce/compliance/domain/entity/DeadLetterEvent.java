package org.openphc.cce.compliance.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.openphc.cce.compliance.domain.enums.FailureStage;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Dead-letter store — durable fallback for failed events.
 * Records events that could not be processed, with retry tracking.
 */
@Entity
@Table(name = "dead_letter_events", indexes = {
        @Index(name = "idx_dead_letter_unresolved", columnList = "next_retry_at")
})
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @Column(name = "failure_reason", nullable = false)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", nullable = false)
    private FailureStage failureStage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    public DeadLetterEvent() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public FailureStage getFailureStage() {
        return failureStage;
    }

    public void setFailureStage(FailureStage failureStage) {
        this.failureStage = failureStage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(OffsetDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
