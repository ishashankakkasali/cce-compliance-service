package org.openphc.cce.compliance.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for StepInstance responses.
 */
public record StepInstanceDto(
        UUID id,
        UUID protocolInstanceId,
        String actionId,
        int repeatIndex,
        String state,
        OffsetDateTime dueDate,
        OffsetDateTime overdueDate,
        OffsetDateTime missedDate,
        OffsetDateTime completedAt,
        String completedBySource,
        String completionStatus,
        UUID matchedEventId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
