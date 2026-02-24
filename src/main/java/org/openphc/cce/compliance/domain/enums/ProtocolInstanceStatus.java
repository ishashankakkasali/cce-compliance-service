package org.openphc.cce.compliance.domain.enums;

/**
 * Status of a protocol instance (patient enrollment in a protocol).
 */
public enum ProtocolInstanceStatus {
    ACTIVE("active"),
    COMPLETED("completed"),
    WITHDRAWN("withdrawn"),
    EXPIRED("expired");

    private final String value;

    ProtocolInstanceStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProtocolInstanceStatus fromValue(String value) {
        for (ProtocolInstanceStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ProtocolInstanceStatus: " + value);
    }
}
