package org.openphc.cce.compliance.domain.enums;

/**
 * State of a step instance within a protocol instance.
 * Follows the lifecycle: pending → due → overdue → missed/completed/skipped.
 */
public enum StepState {
    PENDING("pending"),
    DUE("due"),
    OVERDUE("overdue"),
    MISSED("missed"),
    COMPLETED("completed"),
    SKIPPED("skipped");

    private final String value;

    StepState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static StepState fromValue(String value) {
        for (StepState state : values()) {
            if (state.value.equalsIgnoreCase(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown StepState: " + value);
    }
}
