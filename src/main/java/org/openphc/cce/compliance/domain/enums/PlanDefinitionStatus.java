package org.openphc.cce.compliance.domain.enums;

/**
 * Status of a PlanDefinition resource.
 */
public enum PlanDefinitionStatus {
    ACTIVE("active"),
    RETIRED("retired");

    private final String value;

    PlanDefinitionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PlanDefinitionStatus fromValue(String value) {
        for (PlanDefinitionStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown PlanDefinitionStatus: " + value);
    }
}
