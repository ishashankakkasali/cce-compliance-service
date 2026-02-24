package org.openphc.cce.compliance.domain.entity;

import jakarta.persistence.*;
import org.openphc.cce.compliance.domain.enums.CompletionStatus;
import org.openphc.cce.compliance.domain.enums.StepState;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Step instance — individual step occurrence within a protocol instance.
 * Tracks the lifecycle of a single action within a patient's protocol journey.
 */
@Entity
@Table(name = "step_instance", indexes = {
        @Index(name = "idx_step_instance_protocol", columnList = "protocol_instance_id"),
        @Index(name = "idx_step_instance_state", columnList = "state"),
        @Index(name = "idx_step_instance_due_date", columnList = "due_date")
})
public class StepInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_instance_id", nullable = false)
    private ProtocolInstance protocolInstance;

    @Column(name = "action_id", nullable = false)
    private String actionId;

    @Column(name = "repeat_index", nullable = false)
    private int repeatIndex = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepState state;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Column(name = "overdue_date")
    private OffsetDateTime overdueDate;

    @Column(name = "missed_date")
    private OffsetDateTime missedDate;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by_source")
    private String completedBySource;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_status")
    private CompletionStatus completionStatus;

    @Column(name = "matched_event_id")
    private UUID matchedEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public StepInstance() {
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProtocolInstance getProtocolInstance() {
        return protocolInstance;
    }

    public void setProtocolInstance(ProtocolInstance protocolInstance) {
        this.protocolInstance = protocolInstance;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public int getRepeatIndex() {
        return repeatIndex;
    }

    public void setRepeatIndex(int repeatIndex) {
        this.repeatIndex = repeatIndex;
    }

    public StepState getState() {
        return state;
    }

    public void setState(StepState state) {
        this.state = state;
    }

    public OffsetDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(OffsetDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public OffsetDateTime getOverdueDate() {
        return overdueDate;
    }

    public void setOverdueDate(OffsetDateTime overdueDate) {
        this.overdueDate = overdueDate;
    }

    public OffsetDateTime getMissedDate() {
        return missedDate;
    }

    public void setMissedDate(OffsetDateTime missedDate) {
        this.missedDate = missedDate;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getCompletedBySource() {
        return completedBySource;
    }

    public void setCompletedBySource(String completedBySource) {
        this.completedBySource = completedBySource;
    }

    public CompletionStatus getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(CompletionStatus completionStatus) {
        this.completionStatus = completionStatus;
    }

    public UUID getMatchedEventId() {
        return matchedEventId;
    }

    public void setMatchedEventId(UUID matchedEventId) {
        this.matchedEventId = matchedEventId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
