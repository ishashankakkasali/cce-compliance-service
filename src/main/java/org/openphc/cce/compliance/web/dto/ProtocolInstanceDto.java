package org.openphc.cce.compliance.web.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for ProtocolInstance responses.
 */
public record ProtocolInstanceDto(
        UUID id,
        String patientId,
        String protocolCanonical,
        UUID planDefinitionId,
        String facilityId,
        OffsetDateTime enrolledAt,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<StepInstanceDto> steps,
        List<DeviationDto> deviations
) {
}
