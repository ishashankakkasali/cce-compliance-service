package org.openphc.cce.compliance.domain.enums;

/**
 * Type of deviation from expected protocol pathway.
 */
public enum DeviationType {
    OVERDUE("overdue"),
    MISSED("missed"),
    AMBIGUOUS("ambiguous");

    private final String value;

    DeviationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DeviationType fromValue(String value) {
        for (DeviationType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DeviationType: " + value);
    }
}
