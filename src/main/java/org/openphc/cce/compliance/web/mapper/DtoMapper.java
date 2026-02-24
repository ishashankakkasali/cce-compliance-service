package org.openphc.cce.compliance.web.mapper;

import org.openphc.cce.compliance.domain.entity.*;
import org.openphc.cce.compliance.web.dto.*;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps domain entities to DTOs.
 */
@Component
public class DtoMapper {

    public PlanDefinitionDto toPlanDefinitionDto(PlanDefinitionEntity entity) {
        return new PlanDefinitionDto(
                entity.getId(),
                entity.getUrl(),
                entity.getVersion(),
                entity.getCanonical(),
                entity.getStatus().getValue(),
                entity.getLoadedAt(),
                entity.getDefinition()
        );
    }

    public ProtocolInstanceDto toProtocolInstanceDto(ProtocolInstance entity) {
        return toProtocolInstanceDto(entity, true);
    }

    public ProtocolInstanceDto toProtocolInstanceDto(ProtocolInstance entity, boolean includeChildren) {
        List<StepInstanceDto> steps = Collections.emptyList();
        List<DeviationDto> deviations = Collections.emptyList();

        if (includeChildren) {
            if (entity.getStepInstances() != null) {
                steps = entity.getStepInstances().stream()
                        .map(this::toStepInstanceDto)
                        .collect(Collectors.toList());
            }
            if (entity.getDeviations() != null) {
                deviations = entity.getDeviations().stream()
                        .map(this::toDeviationDto)
                        .collect(Collectors.toList());
            }
        }

        return new ProtocolInstanceDto(
                entity.getId(),
                entity.getPatientId(),
                entity.getProtocolCanonical(),
                entity.getPlanDefinition() != null ? entity.getPlanDefinition().getId() : null,
                entity.getEnrolledAt(),
                entity.getStatus().getValue(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                steps,
                deviations
        );
    }

    public StepInstanceDto toStepInstanceDto(StepInstance entity) {
        return new StepInstanceDto(
                entity.getId(),
                entity.getProtocolInstance() != null ? entity.getProtocolInstance().getId() : null,
                entity.getActionId(),
                entity.getRepeatIndex(),
                entity.getState().getValue(),
                entity.getDueDate(),
                entity.getOverdueDate(),
                entity.getMissedDate(),
                entity.getCompletedAt(),
                entity.getCompletedBySource(),
                entity.getCompletionStatus() != null ? entity.getCompletionStatus().getValue() : null,
                entity.getMatchedEventId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public DeviationDto toDeviationDto(Deviation entity) {
        return new DeviationDto(
                entity.getId(),
                entity.getProtocolInstance() != null ? entity.getProtocolInstance().getId() : null,
                entity.getStepInstance() != null ? entity.getStepInstance().getId() : null,
                entity.getDeviationType().getValue(),
                entity.getDetectedAt(),
                entity.getIntelligenceEventId(),
                entity.getMetadata()
        );
    }

    public EventLogDto toEventLogDto(EventLog entity) {
        return new EventLogDto(
                entity.getId(),
                entity.getCloudeventsId(),
                entity.getSource(),
                entity.getSourceEventId(),
                entity.getSubject(),
                entity.getType(),
                entity.getEventTime(),
                entity.getReceivedAt(),
                entity.getCorrelationId(),
                entity.getData(),
                entity.getProtocolInstanceId(),
                entity.getProtocolDefinitionId(),
                entity.getActionId(),
                entity.getFacilityId(),
                entity.getProcessingStatus() != null ? entity.getProcessingStatus().getValue() : null,
                entity.getMatchedStepInstanceId()
        );
    }

    public List<PlanDefinitionDto> toPlanDefinitionDtoList(List<PlanDefinitionEntity> entities) {
        return entities.stream().map(this::toPlanDefinitionDto).collect(Collectors.toList());
    }

    public List<ProtocolInstanceDto> toProtocolInstanceDtoList(List<ProtocolInstance> entities) {
        return entities.stream().map(e -> toProtocolInstanceDto(e, false)).collect(Collectors.toList());
    }
}
