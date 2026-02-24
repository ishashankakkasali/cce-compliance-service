package org.openphc.cce.compliance.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for Deviation responses.
 */
public record DeviationDto(
        UUID id,
        UUID protocolInstanceId,
        UUID stepInstanceId,
        String deviationType,
        OffsetDateTime detectedAt,
        UUID intelligenceEventId,
        Map<String, Object> metadata
) {
}
