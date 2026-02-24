package org.openphc.cce.compliance.domain.enums;

/**
 * Completion status — how a step was completed relative to its due date.
 */
public enum CompletionStatus {
    ON_TIME("on_time"),
    EARLY("early"),
    LATE("late");

    private final String value;

    CompletionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CompletionStatus fromValue(String value) {
        for (CompletionStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown CompletionStatus: " + value);
    }
}
