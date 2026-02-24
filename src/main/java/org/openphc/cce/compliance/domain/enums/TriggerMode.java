package org.openphc.cce.compliance.domain.enums;

/**
 * Trigger mode for a trigger index entry.
 */
public enum TriggerMode {
    DATA_ADDED("data-added"),
    NAMED_EVENT("named-event");

    private final String value;

    TriggerMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TriggerMode fromValue(String value) {
        for (TriggerMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown TriggerMode: " + value);
    }
}
