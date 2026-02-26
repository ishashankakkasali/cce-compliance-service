package org.openphc.cce.compliance.domain.entity;

import jakarta.persistence.*;
import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Protocol instance — a patient's enrollment in a specific protocol.
 * Represents an active journey through a PlanDefinition.
 */
@Entity
@Table(name = "protocol_instance", indexes = {
        @Index(name = "idx_protocol_instance_patient", columnList = "patient_id"),
        @Index(name = "idx_protocol_instance_status", columnList = "status"),
        @Index(name = "idx_protocol_instance_facility", columnList = "facility_id")
})
public class ProtocolInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "protocol_canonical", nullable = false)
    private String protocolCanonical;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_definition_id", nullable = false)
    private PlanDefinitionEntity planDefinition;

    @Column(name = "facility_id")
    private String facilityId;

    @Column(name = "enrolled_at", nullable = false)
    private OffsetDateTime enrolledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProtocolInstanceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "protocolInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StepInstance> stepInstances = new ArrayList<>();

    @OneToMany(mappedBy = "protocolInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Deviation> deviations = new ArrayList<>();

    public ProtocolInstance() {
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (enrolledAt == null) enrolledAt = now;
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

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getProtocolCanonical() {
        return protocolCanonical;
    }

    public void setProtocolCanonical(String protocolCanonical) {
        this.protocolCanonical = protocolCanonical;
    }

    public PlanDefinitionEntity getPlanDefinition() {
        return planDefinition;
    }

    public void setPlanDefinition(PlanDefinitionEntity planDefinition) {
        this.planDefinition = planDefinition;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }

    public OffsetDateTime getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(OffsetDateTime enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public ProtocolInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(ProtocolInstanceStatus status) {
        this.status = status;
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

    public List<StepInstance> getStepInstances() {
        return stepInstances;
    }

    public void setStepInstances(List<StepInstance> stepInstances) {
        this.stepInstances = stepInstances;
    }

    public List<Deviation> getDeviations() {
        return deviations;
    }

    public void setDeviations(List<Deviation> deviations) {
        this.deviations = deviations;
    }
}
