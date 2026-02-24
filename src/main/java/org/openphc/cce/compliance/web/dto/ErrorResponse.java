package org.openphc.cce.compliance.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standardized error response structure.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        OffsetDateTime timestamp,
        List<FieldError> fieldErrors
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, OffsetDateTime.now(), null);
    }

    public record FieldError(String field, String message) {
    }
}
