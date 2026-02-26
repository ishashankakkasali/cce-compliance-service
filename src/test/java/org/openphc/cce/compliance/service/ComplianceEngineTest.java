package org.openphc.cce.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.domain.entity.*;
import org.openphc.cce.compliance.domain.enums.*;
import org.openphc.cce.compliance.fhir.ExpressionEvaluationService;
import org.openphc.cce.compliance.fhir.PlanDefinitionParser;
import org.openphc.cce.compliance.kafka.model.CloudEventMessage;
import org.openphc.cce.compliance.kafka.producer.DeadLetterProducer;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ComplianceEngine Tests")
class ComplianceEngineTest {

    @Mock private EventLogService eventLogService;
    @Mock private TriggerMatchingService triggerMatchingService;
    @Mock private ProtocolDefinitionService protocolDefinitionService;
    @Mock private ProtocolInstanceService protocolInstanceService;
    @Mock private StepInstanceService stepInstanceService;
    @Mock private DeviationService deviationService;
    @Mock private AuditService auditService;
    @Mock private DeadLetterProducer deadLetterProducer;
    @Mock private PlanDefinitionParser planDefinitionParser;
    @Mock private ExpressionEvaluationService expressionEvaluationService;
    @Mock private ObjectMapper objectMapper;

    private MeterRegistry meterRegistry;
    private ComplianceEngine engine;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        engine = new ComplianceEngine(
                eventLogService, triggerMatchingService, protocolDefinitionService,
                protocolInstanceService, stepInstanceService, deviationService,
                auditService, deadLetterProducer,
                planDefinitionParser, expressionEvaluationService,
                objectMapper, meterRegistry);
    }

    private CloudEventMessage createEvent(String id, String resourceType) {
        CloudEventMessage event = new CloudEventMessage();
        event.setId(id);
        event.setSource("source-1");
        event.setType("org.openhie.cr." + resourceType.toLowerCase());
        event.setSubject("Patient/123");
        event.setTime(OffsetDateTime.now());
        Map<String, Object> data = new HashMap<>();
        data.put("resourceType", resourceType);
        event.setData(data);
        return event;
    }

    @Nested
    @DisplayName("processInboundEvent - idempotency")
    class Idempotency {

        @Test
        @DisplayName("should skip duplicate events")
        void shouldSkipDuplicate() {
            CloudEventMessage event = createEvent("ce-dup", "Encounter");
            when(eventLogService.isDuplicate("ce-dup", "source-1")).thenReturn(true);

            engine.processInboundEvent(event);

            verify(eventLogService, never()).recordEvent(any(), any());
        }
    }

    @Nested
    @DisplayName("processInboundEvent - zero match")
    class ZeroMatch {

        @Test
        @DisplayName("should record event with ZERO_MATCH when no structural matches")
        void shouldRecordZeroMatchWhenNoMatches() {
            CloudEventMessage event = createEvent("ce-no-match", "Encounter");
            EventLog eventLog = new EventLog();
            eventLog.setId(UUID.randomUUID());

            when(eventLogService.isDuplicate(anyString(), anyString())).thenReturn(false);
            when(eventLogService.recordEvent(any(), eq(ProcessingStatus.ZERO_MATCH))).thenReturn(eventLog);
            when(triggerMatchingService.findStructuralMatches("Encounter", null, null))
                    .thenReturn(Collections.emptyList());

            engine.processInboundEvent(event);

            verify(eventLogService).recordEvent(event, ProcessingStatus.ZERO_MATCH);
            verify(protocolInstanceService, never()).enrollOrGetActive(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("processInboundEvent - single match")
    class SingleMatch {

        @Test
        @DisplayName("should process single match and complete step")
        void shouldProcessSingleMatch() throws Exception {
            CloudEventMessage event = createEvent("ce-match", "Encounter");
            EventLog eventLog = new EventLog();
            eventLog.setId(UUID.randomUUID());

            UUID pdId = UUID.randomUUID();
            TriggerIndex triggerIdx = new TriggerIndex();
            triggerIdx.setResourceType("Encounter");
            triggerIdx.setPlanDefinitionId(pdId);
            triggerIdx.setActionId("action-1");

            PlanDefinitionEntity pdEntity = new PlanDefinitionEntity();
            pdEntity.setId(pdId);
            pdEntity.setUrl("http://example.org/pd");
            pdEntity.setVersion("1.0");
            pdEntity.setStatus(PlanDefinitionStatus.ACTIVE);
            pdEntity.setDefinition(Map.of("resourceType", "PlanDefinition"));

            PlanDefinition pd = new PlanDefinition();
            PlanDefinition.PlanDefinitionActionComponent action = pd.addAction();
            action.setId("action-1");

            ProtocolInstance pi = new ProtocolInstance();
            pi.setId(UUID.randomUUID());
            pi.setPatientId("Patient/123");
            pi.setProtocolCanonical("http://example.org/pd|1.0");

            StepInstance step = new StepInstance();
            step.setId(UUID.randomUUID());
            step.setActionId("action-1");
            step.setState(StepState.DUE);

            StepInstance completedStep = new StepInstance();
            completedStep.setId(step.getId());
            completedStep.setCompletionStatus(CompletionStatus.ON_TIME);

            when(eventLogService.isDuplicate(anyString(), anyString())).thenReturn(false);
            when(eventLogService.recordEvent(any(), any())).thenReturn(eventLog);
            when(triggerMatchingService.findStructuralMatches("Encounter", null, null))
                    .thenReturn(List.of(triggerIdx));
            when(protocolDefinitionService.findById(pdId)).thenReturn(Optional.of(pdEntity));
            when(objectMapper.writeValueAsString(pdEntity.getDefinition())).thenReturn("{}");
            when(planDefinitionParser.parse("{}")).thenReturn(pd);
            when(planDefinitionParser.extractAllActions(pd)).thenReturn(List.of(action));
            when(expressionEvaluationService.buildVariables(any(), any(), any(), any()))
                    .thenReturn(Map.of("event", Map.of()));
            when(triggerMatchingService.evaluateCondition(eq(action), eq(triggerIdx), any())).thenReturn(true);
            when(protocolInstanceService.enrollOrGetActive(eq("Patient/123"), eq(pdEntity), any())).thenReturn(pi);
            when(planDefinitionParser.extractTiming(action)).thenReturn(Map.of());
            when(planDefinitionParser.extractToleranceDays(action)).thenReturn(null);
            when(stepInstanceService.createStep(eq(pi), eq("action-1"), isNull(), isNull(), isNull()))
                    .thenReturn(step);
            when(stepInstanceService.completeStep(step.getId(), eventLog.getId(), "source-1"))
                    .thenReturn(completedStep);
            when(planDefinitionParser.findDependentActions(any(), eq("action-1")))
                    .thenReturn(Collections.emptyList());

            engine.processInboundEvent(event);

            verify(protocolInstanceService).enrollOrGetActive(eq("Patient/123"), eq(pdEntity), any());
            verify(stepInstanceService).completeStep(step.getId(), eventLog.getId(), "source-1");
            verify(eventLogService).updateMatchResult(
                    eq(eventLog.getId()), eq(pi.getId()), eq(pdEntity.getId()),
                    eq("action-1"), eq(completedStep.getId()), eq(ProcessingStatus.MATCHED));
            verify(auditService).auditSystem(eq("COMPLIANCE"), eq("STEP_COMPLETED"), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("processInboundEvent - null resource type")
    class NullResourceType {

        @Test
        @DisplayName("should handle event with no resource type")
        void shouldHandleNoResourceType() {
            CloudEventMessage event = new CloudEventMessage();
            event.setId("ce-no-type");
            event.setSource("source-1");
            event.setData(Map.of()); // no resourceType
            event.setType(null); // no type either

            EventLog eventLog = new EventLog();
            eventLog.setId(UUID.randomUUID());

            when(eventLogService.isDuplicate(anyString(), anyString())).thenReturn(false);
            when(eventLogService.recordEvent(any(), any())).thenReturn(eventLog);

            engine.processInboundEvent(event);

            verify(triggerMatchingService, never()).findStructuralMatches(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("processInboundEvent - error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should send to dead letter on exception")
        void shouldSendToDeadLetterOnException() {
            CloudEventMessage event = createEvent("ce-err", "Encounter");
            EventLog eventLog = new EventLog();
            eventLog.setId(UUID.randomUUID());

            when(eventLogService.isDuplicate(anyString(), anyString())).thenReturn(false);
            when(eventLogService.recordEvent(any(), any())).thenReturn(eventLog);
            when(triggerMatchingService.findStructuralMatches(any(), any(), any()))
                    .thenThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() -> engine.processInboundEvent(event))
                    .isInstanceOf(RuntimeException.class);

            verify(deadLetterProducer).publishDeadLetter(eq(event), eq("DB error"),
                    eq(FailureStage.PROCESSING), any());
        }
    }

    @Nested
    @DisplayName("processInboundEvent - resource type extraction")
    class ResourceTypeExtraction {

        @Test
        @DisplayName("should extract resource type from event data")
        void shouldExtractFromData() {
            CloudEventMessage event = createEvent("ce-data", "Observation");

            EventLog eventLog = new EventLog();
            eventLog.setId(UUID.randomUUID());

            when(eventLogService.isDuplicate(anyString(), anyString())).thenReturn(false);
            when(eventLogService.recordEvent(any(), any())).thenReturn(eventLog);
            when(triggerMatchingService.findStructuralMatches("Observation", null, null))
                    .thenReturn(Collections.emptyList());

            engine.processInboundEvent(event);

            verify(triggerMatchingService).findStructuralMatches("Observation", null, null);
        }

        @Test
        @DisplayName("should infer resource type from event type when data has no resourceType")
        void shouldInferFromEventType() {
            CloudEventMessage event = new CloudEventMessage();
            event.setId("ce-infer");
            event.setSource("source-1");
            event.setType("org.openhie.cr.encounter");
            event.setSubject("Patient/123");
            event.setData(Map.of("status", "finished")); // no resourceType

            EventLog eventLog = new EventLog();
            eventLog.setId(UUID.randomUUID());

            when(eventLogService.isDuplicate(anyString(), anyString())).thenReturn(false);
            when(eventLogService.recordEvent(any(), any())).thenReturn(eventLog);
            // New: bulk fetch by resource type, then in-memory filter
            when(triggerMatchingService.findStructuralMatches("Encounter", null, null))
                    .thenReturn(Collections.emptyList());

            engine.processInboundEvent(event);

            verify(triggerMatchingService).findStructuralMatches("Encounter", null, null);
        }
    }

    @Nested
    @DisplayName("processInboundEvent - code extraction")
    class CodeExtraction {

        @Test
        @DisplayName("should extract code system and value from nested data")
        void shouldExtractCodeFromData() throws Exception {
            CloudEventMessage event = new CloudEventMessage();
            event.setId("ce-code");
            event.setSource("source-1");
            event.setType("org.openhie.cr.observation");
            event.setSubject("Patient/123");
            Map<String, Object> data = new HashMap<>();
            data.put("resourceType", "Observation");
            data.put("code", Map.of("coding", List.of(
                    Map.of("system", "http://loinc.org", "code", "12345-6")
            )));
            event.setData(data);

            EventLog eventLog = new EventLog();
            eventLog.setId(UUID.randomUUID());

            when(eventLogService.isDuplicate(anyString(), anyString())).thenReturn(false);
            when(eventLogService.recordEvent(any(), any())).thenReturn(eventLog);
            // New: bulk fetch by resource type, then in-memory filtering
            when(triggerMatchingService.findStructuralMatches("Observation", null, null))
                    .thenReturn(Collections.emptyList());

            engine.processInboundEvent(event);

            verify(triggerMatchingService).findStructuralMatches("Observation", null, null);
        }
    }
}
