package org.openphc.cce.compliance.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for EventLog responses.
 */
public record EventLogDto(
        UUID id,
        String cloudeventsId,
        String source,
        String sourceEventId,
        String subject,
        String type,
        OffsetDateTime eventTime,
        OffsetDateTime receivedAt,
        String correlationId,
        Map<String, Object> data,
        UUID protocolInstanceId,
        UUID protocolDefinitionId,
        String actionId,
        String facilityId,
        String processingStatus,
        UUID matchedStepInstanceId
) {
}
