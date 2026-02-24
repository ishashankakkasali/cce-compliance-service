package org.openphc.cce.compliance.domain.enums;

/**
 * Processing status of an event in the event log.
 */
public enum ProcessingStatus {
    MATCHED("matched"),
    ZERO_MATCH("zero_match"),
    AMBIGUOUS("ambiguous"),
    DUPLICATE("duplicate");

    private final String value;

    ProcessingStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProcessingStatus fromValue(String value) {
        for (ProcessingStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ProcessingStatus: " + value);
    }
}
