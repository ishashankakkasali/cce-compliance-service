package org.openphc.cce.compliance.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for PlanDefinition responses.
 */
public record PlanDefinitionDto(
        UUID id,
        String url,
        String version,
        String canonical,
        String status,
        OffsetDateTime loadedAt,
        Map<String, Object> definition
) {
}
