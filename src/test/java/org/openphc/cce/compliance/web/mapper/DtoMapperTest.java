package org.openphc.cce.compliance.web.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openphc.cce.compliance.domain.entity.*;
import org.openphc.cce.compliance.domain.enums.*;
import org.openphc.cce.compliance.web.dto.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DtoMapper Tests")
class DtoMapperTest {

    private DtoMapper dtoMapper;

    @BeforeEach
    void setUp() {
        dtoMapper = new DtoMapper();
    }

    @Nested
    @DisplayName("toPlanDefinitionDto")
    class ToPlanDefinitionDto {

        @Test
        @DisplayName("should map all fields correctly")
        void shouldMapAllFields() {
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            entity.setId(UUID.randomUUID());
            entity.setUrl("http://example.org/pd");
            entity.setVersion("1.0");
            entity.setStatus(PlanDefinitionStatus.ACTIVE);
            entity.setLoadedAt(OffsetDateTime.now());
            entity.setDefinition(Map.of("resourceType", "PlanDefinition"));

            PlanDefinitionDto dto = dtoMapper.toPlanDefinitionDto(entity);

            assertThat(dto.id()).isEqualTo(entity.getId());
            assertThat(dto.url()).isEqualTo("http://example.org/pd");
            assertThat(dto.version()).isEqualTo("1.0");
            assertThat(dto.canonical()).isEqualTo("http://example.org/pd|1.0");
            assertThat(dto.status()).isEqualTo("active");
            assertThat(dto.loadedAt()).isEqualTo(entity.getLoadedAt());
            assertThat(dto.definition()).containsEntry("resourceType", "PlanDefinition");
        }
    }

    @Nested
    @DisplayName("toProtocolInstanceDto")
    class ToProtocolInstanceDto {

        private ProtocolInstance createInstance() {
            PlanDefinitionEntity pd = new PlanDefinitionEntity();
            pd.setId(UUID.randomUUID());

            ProtocolInstance pi = new ProtocolInstance();
            pi.setId(UUID.randomUUID());
            pi.setPatientId("Patient/123");
            pi.setProtocolCanonical("http://example.org/pd|1.0");
            pi.setPlanDefinition(pd);
            pi.setStatus(ProtocolInstanceStatus.ACTIVE);
            pi.setEnrolledAt(OffsetDateTime.now());
            pi.setCreatedAt(OffsetDateTime.now());
            pi.setUpdatedAt(OffsetDateTime.now());
            return pi;
        }

        @Test
        @DisplayName("should map basic fields")
        void shouldMapBasicFields() {
            ProtocolInstance pi = createInstance();

            ProtocolInstanceDto dto = dtoMapper.toProtocolInstanceDto(pi);

            assertThat(dto.id()).isEqualTo(pi.getId());
            assertThat(dto.patientId()).isEqualTo("Patient/123");
            assertThat(dto.protocolCanonical()).isEqualTo("http://example.org/pd|1.0");
            assertThat(dto.planDefinitionId()).isEqualTo(pi.getPlanDefinition().getId());
            assertThat(dto.status()).isEqualTo("active");
        }

        @Test
        @DisplayName("should include children when includeChildren=true")
        void shouldIncludeChildren() {
            ProtocolInstance pi = createInstance();
            StepInstance step = new StepInstance();
            step.setId(UUID.randomUUID());
            step.setProtocolInstance(pi);
            step.setActionId("action-1");
            step.setRepeatIndex(0);
            step.setState(StepState.PENDING);
            step.setCreatedAt(OffsetDateTime.now());
            step.setUpdatedAt(OffsetDateTime.now());
            pi.setStepInstances(List.of(step));

            ProtocolInstanceDto dto = dtoMapper.toProtocolInstanceDto(pi, true);

            assertThat(dto.steps()).hasSize(1);
            assertThat(dto.steps().get(0).actionId()).isEqualTo("action-1");
        }

        @Test
        @DisplayName("should exclude children when includeChildren=false")
        void shouldExcludeChildren() {
            ProtocolInstance pi = createInstance();
            pi.setStepInstances(List.of(new StepInstance()));

            ProtocolInstanceDto dto = dtoMapper.toProtocolInstanceDto(pi, false);

            assertThat(dto.steps()).isEmpty();
            assertThat(dto.deviations()).isEmpty();
        }

        @Test
        @DisplayName("should handle null planDefinition gracefully")
        void shouldHandleNullPlanDefinition() {
            ProtocolInstance pi = createInstance();
            pi.setPlanDefinition(null);

            ProtocolInstanceDto dto = dtoMapper.toProtocolInstanceDto(pi);

            assertThat(dto.planDefinitionId()).isNull();
        }
    }

    @Nested
    @DisplayName("toStepInstanceDto")
    class ToStepInstanceDto {

        @Test
        @DisplayName("should map all step fields")
        void shouldMapAllFields() {
            ProtocolInstance pi = new ProtocolInstance();
            pi.setId(UUID.randomUUID());

            StepInstance step = new StepInstance();
            step.setId(UUID.randomUUID());
            step.setProtocolInstance(pi);
            step.setActionId("immunization-step");
            step.setRepeatIndex(2);
            step.setState(StepState.COMPLETED);
            step.setDueDate(OffsetDateTime.now().minusDays(1));
            step.setCompletedAt(OffsetDateTime.now());
            step.setCompletedBySource("ehr-system");
            step.setCompletionStatus(CompletionStatus.ON_TIME);
            step.setMatchedEventId(UUID.randomUUID());
            step.setCreatedAt(OffsetDateTime.now());
            step.setUpdatedAt(OffsetDateTime.now());

            StepInstanceDto dto = dtoMapper.toStepInstanceDto(step);

            assertThat(dto.id()).isEqualTo(step.getId());
            assertThat(dto.protocolInstanceId()).isEqualTo(pi.getId());
            assertThat(dto.actionId()).isEqualTo("immunization-step");
            assertThat(dto.repeatIndex()).isEqualTo(2);
            assertThat(dto.state()).isEqualTo("completed");
            assertThat(dto.completionStatus()).isEqualTo("on_time");
        }

        @Test
        @DisplayName("should handle null completion status")
        void shouldHandleNullCompletionStatus() {
            StepInstance step = new StepInstance();
            step.setId(UUID.randomUUID());
            step.setActionId("step-1");
            step.setState(StepState.PENDING);
            step.setCreatedAt(OffsetDateTime.now());
            step.setUpdatedAt(OffsetDateTime.now());

            StepInstanceDto dto = dtoMapper.toStepInstanceDto(step);

            assertThat(dto.completionStatus()).isNull();
            assertThat(dto.protocolInstanceId()).isNull();
        }
    }

    @Nested
    @DisplayName("toDeviationDto")
    class ToDeviationDto {

        @Test
        @DisplayName("should map deviation fields")
        void shouldMapFields() {
            ProtocolInstance pi = new ProtocolInstance();
            pi.setId(UUID.randomUUID());
            StepInstance si = new StepInstance();
            si.setId(UUID.randomUUID());

            Deviation deviation = new Deviation();
            deviation.setId(UUID.randomUUID());
            deviation.setProtocolInstance(pi);
            deviation.setStepInstance(si);
            deviation.setDeviationType(DeviationType.MISSED);
            deviation.setDetectedAt(OffsetDateTime.now());
            deviation.setIntelligenceEventId(UUID.randomUUID());
            deviation.setMetadata(Map.of("reason", "timeout"));

            DeviationDto dto = dtoMapper.toDeviationDto(deviation);

            assertThat(dto.id()).isEqualTo(deviation.getId());
            assertThat(dto.protocolInstanceId()).isEqualTo(pi.getId());
            assertThat(dto.stepInstanceId()).isEqualTo(si.getId());
            assertThat(dto.deviationType()).isEqualTo("missed");
            assertThat(dto.metadata()).containsEntry("reason", "timeout");
        }
    }

    @Nested
    @DisplayName("toEventLogDto")
    class ToEventLogDto {

        @Test
        @DisplayName("should map event log fields")
        void shouldMapFields() {
            EventLog log = new EventLog();
            log.setId(UUID.randomUUID());
            log.setCloudeventsId("ce-123");
            log.setSource("ehr-system");
            log.setSourceEventId("evt-456");
            log.setSubject("Patient/123");
            log.setType("org.openphc.fhir.Observation.create");
            log.setEventTime(OffsetDateTime.now());
            log.setReceivedAt(OffsetDateTime.now());
            log.setCorrelationId("corr-1");
            log.setData(Map.of("key", "value"));
            log.setProcessingStatus(ProcessingStatus.MATCHED);
            log.setMatchedStepInstanceId(UUID.randomUUID());

            EventLogDto dto = dtoMapper.toEventLogDto(log);

            assertThat(dto.id()).isEqualTo(log.getId());
            assertThat(dto.cloudeventsId()).isEqualTo("ce-123");
            assertThat(dto.source()).isEqualTo("ehr-system");
            assertThat(dto.processingStatus()).isEqualTo("matched");
        }

        @Test
        @DisplayName("should handle null processing status")
        void shouldHandleNullProcessingStatus() {
            EventLog log = new EventLog();
            log.setId(UUID.randomUUID());
            log.setCloudeventsId("ce-1");
            log.setSource("src");
            log.setSubject("Patient/1");
            log.setType("type");
            log.setEventTime(OffsetDateTime.now());
            log.setReceivedAt(OffsetDateTime.now());
            log.setCorrelationId("c");
            log.setData(Map.of());
            log.setProcessingStatus(null);

            EventLogDto dto = dtoMapper.toEventLogDto(log);

            assertThat(dto.processingStatus()).isNull();
        }
    }

    @Nested
    @DisplayName("List mapping methods")
    class ListMapping {

        @Test
        @DisplayName("toPlanDefinitionDtoList should map list")
        void shouldMapPlanDefinitionList() {
            PlanDefinitionEntity e1 = new PlanDefinitionEntity();
            e1.setId(UUID.randomUUID());
            e1.setUrl("http://example.org/pd1");
            e1.setVersion("1.0");
            e1.setStatus(PlanDefinitionStatus.ACTIVE);
            e1.setLoadedAt(OffsetDateTime.now());
            e1.setDefinition(Map.of());

            PlanDefinitionEntity e2 = new PlanDefinitionEntity();
            e2.setId(UUID.randomUUID());
            e2.setUrl("http://example.org/pd2");
            e2.setVersion("2.0");
            e2.setStatus(PlanDefinitionStatus.RETIRED);
            e2.setLoadedAt(OffsetDateTime.now());
            e2.setDefinition(Map.of());

            List<PlanDefinitionDto> dtos = dtoMapper.toPlanDefinitionDtoList(List.of(e1, e2));

            assertThat(dtos).hasSize(2);
        }

        @Test
        @DisplayName("toProtocolInstanceDtoList should map without children")
        void shouldMapProtocolInstanceListWithoutChildren() {
            PlanDefinitionEntity pd = new PlanDefinitionEntity();
            pd.setId(UUID.randomUUID());

            ProtocolInstance pi = new ProtocolInstance();
            pi.setId(UUID.randomUUID());
            pi.setPatientId("Patient/1");
            pi.setProtocolCanonical("canonical");
            pi.setPlanDefinition(pd);
            pi.setStatus(ProtocolInstanceStatus.ACTIVE);
            pi.setEnrolledAt(OffsetDateTime.now());
            pi.setCreatedAt(OffsetDateTime.now());
            pi.setUpdatedAt(OffsetDateTime.now());

            StepInstance step = new StepInstance();
            step.setId(UUID.randomUUID());
            step.setActionId("a");
            step.setState(StepState.PENDING);
            step.setCreatedAt(OffsetDateTime.now());
            step.setUpdatedAt(OffsetDateTime.now());
            pi.setStepInstances(List.of(step));

            List<ProtocolInstanceDto> dtos = dtoMapper.toProtocolInstanceDtoList(List.of(pi));

            assertThat(dtos).hasSize(1);
            assertThat(dtos.get(0).steps()).isEmpty(); // children excluded in list mapping
        }
    }
}
