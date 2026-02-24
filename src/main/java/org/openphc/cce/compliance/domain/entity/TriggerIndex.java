package org.openphc.cce.compliance.domain.entity;

import jakarta.persistence.*;
import org.openphc.cce.compliance.domain.enums.TriggerMode;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Trigger index — inverted index for Tier 1 structural matching.
 * Built at protocol load time; enables fast candidate lookup by resource type + code.
 */
@Entity
@Table(name = "trigger_index", indexes = {
        @Index(name = "idx_trigger_index_resource", columnList = "resource_type"),
        @Index(name = "idx_trigger_index_code", columnList = "resource_type, code_system, code_value")
})
@IdClass(TriggerIndex.TriggerIndexId.class)
public class TriggerIndex {

    @Id
    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Id
    @Column(name = "code_system")
    private String codeSystem;

    @Id
    @Column(name = "code_value")
    private String codeValue;

    @Id
    @Column(name = "plan_definition_id", nullable = false)
    private UUID planDefinitionId;

    @Id
    @Column(name = "action_id", nullable = false)
    private String actionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_mode", nullable = false)
    private TriggerMode triggerMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_definition_id", insertable = false, updatable = false)
    private PlanDefinitionEntity planDefinition;

    public TriggerIndex() {
    }

    // --- Getters and Setters ---

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getCodeSystem() {
        return codeSystem;
    }

    public void setCodeSystem(String codeSystem) {
        this.codeSystem = codeSystem;
    }

    public String getCodeValue() {
        return codeValue;
    }

    public void setCodeValue(String codeValue) {
        this.codeValue = codeValue;
    }

    public UUID getPlanDefinitionId() {
        return planDefinitionId;
    }

    public void setPlanDefinitionId(UUID planDefinitionId) {
        this.planDefinitionId = planDefinitionId;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public TriggerMode getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(TriggerMode triggerMode) {
        this.triggerMode = triggerMode;
    }

    public PlanDefinitionEntity getPlanDefinition() {
        return planDefinition;
    }

    public void setPlanDefinition(PlanDefinitionEntity planDefinition) {
        this.planDefinition = planDefinition;
    }

    /**
     * Composite primary key for TriggerIndex.
     */
    public static class TriggerIndexId implements Serializable {
        private String resourceType;
        private String codeSystem;
        private String codeValue;
        private UUID planDefinitionId;
        private String actionId;

        public TriggerIndexId() {
        }

        public TriggerIndexId(String resourceType, String codeSystem, String codeValue,
                              UUID planDefinitionId, String actionId) {
            this.resourceType = resourceType;
            this.codeSystem = codeSystem != null ? codeSystem : "";
            this.codeValue = codeValue != null ? codeValue : "";
            this.planDefinitionId = planDefinitionId;
            this.actionId = actionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TriggerIndexId that = (TriggerIndexId) o;
            return Objects.equals(resourceType, that.resourceType) &&
                    Objects.equals(codeSystem, that.codeSystem) &&
                    Objects.equals(codeValue, that.codeValue) &&
                    Objects.equals(planDefinitionId, that.planDefinitionId) &&
                    Objects.equals(actionId, that.actionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resourceType, codeSystem, codeValue, planDefinitionId, actionId);
        }
    }
}
