package org.openphc.cce.compliance.domain.enums;

/**
 * Stage at which an event failed, resulting in a dead-letter entry.
 */
public enum FailureStage {
    KAFKA_PUBLISH("kafka_publish"),
    PROCESSING("processing"),
    VALIDATION("validation");

    private final String value;

    FailureStage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FailureStage fromValue(String value) {
        for (FailureStage stage : values()) {
            if (stage.value.equalsIgnoreCase(value)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unknown FailureStage: " + value);
    }
}
