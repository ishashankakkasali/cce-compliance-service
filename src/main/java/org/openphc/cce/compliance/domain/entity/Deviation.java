package org.openphc.cce.compliance.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.openphc.cce.compliance.domain.enums.DeviationType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Deviation — recorded deviations from expected protocol pathway.
 */
@Entity
@Table(name = "deviation", indexes = {
        @Index(name = "idx_deviation_protocol", columnList = "protocol_instance_id"),
        @Index(name = "idx_deviation_type", columnList = "deviation_type")
})
public class Deviation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_instance_id", nullable = false)
    private ProtocolInstance protocolInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_instance_id", nullable = false)
    private StepInstance stepInstance;

    @Enumerated(EnumType.STRING)
    @Column(name = "deviation_type", nullable = false)
    private DeviationType deviationType;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "intelligence_event_id")
    private UUID intelligenceEventId;

    @Type(JsonType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    public Deviation() {
    }

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = OffsetDateTime.now();
        }
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

    public StepInstance getStepInstance() {
        return stepInstance;
    }

    public void setStepInstance(StepInstance stepInstance) {
        this.stepInstance = stepInstance;
    }

    public DeviationType getDeviationType() {
        return deviationType;
    }

    public void setDeviationType(DeviationType deviationType) {
        this.deviationType = deviationType;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(OffsetDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public UUID getIntelligenceEventId() {
        return intelligenceEventId;
    }

    public void setIntelligenceEventId(UUID intelligenceEventId) {
        this.intelligenceEventId = intelligenceEventId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
