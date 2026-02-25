package org.openphc.cce.compliance.service;

import org.hl7.fhir.r4.model.Expression;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.domain.entity.TriggerIndex;
import org.openphc.cce.compliance.domain.enums.TriggerMode;
import org.openphc.cce.compliance.domain.repository.TriggerIndexRepository;
import org.openphc.cce.compliance.fhir.ExpressionEvaluationService;
import org.openphc.cce.compliance.fhir.PlanDefinitionParser;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerMatchingService Tests")
class TriggerMatchingServiceTest {

    @Mock private TriggerIndexRepository triggerIndexRepository;
    @Mock private ExpressionEvaluationService expressionEvaluationService;
    @Mock private PlanDefinitionParser planDefinitionParser;

    private TriggerMatchingService service;

    @BeforeEach
    void setUp() {
        service = new TriggerMatchingService(triggerIndexRepository, expressionEvaluationService, planDefinitionParser);
    }

    private TriggerIndex createTriggerIndex(String resourceType, String codeSystem, String codeValue) {
        TriggerIndex ti = new TriggerIndex();
        ti.setResourceType(resourceType);
        ti.setCodeSystem(codeSystem);
        ti.setCodeValue(codeValue);
        ti.setPlanDefinitionId(UUID.randomUUID());
        ti.setActionId("action-1");
        ti.setTriggerMode(TriggerMode.DATA_ADDED);
        return ti;
    }

    @Nested
    @DisplayName("findStructuralMatches")
    class FindStructuralMatches {

        @Test
        @DisplayName("should find matches by resource type and code")
        void shouldFindByResourceTypeAndCode() {
            TriggerIndex ti = createTriggerIndex("Encounter", "http://loinc.org", "12345-6");
            when(triggerIndexRepository.findByResourceTypeAndCode("Encounter", "http://loinc.org", "12345-6"))
                    .thenReturn(List.of(ti));

            List<TriggerIndex> result = service.findStructuralMatches("Encounter", "http://loinc.org", "12345-6");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getResourceType()).isEqualTo("Encounter");
        }

        @Test
        @DisplayName("should find matches by resource type only when no code")
        void shouldFindByResourceTypeOnly() {
            TriggerIndex ti = createTriggerIndex("Observation", "", "");
            when(triggerIndexRepository.findByResourceType("Observation"))
                    .thenReturn(List.of(ti));

            List<TriggerIndex> result = service.findStructuralMatches("Observation", null, null);

            assertThat(result).hasSize(1);
            verify(triggerIndexRepository).findByResourceType("Observation");
        }

        @Test
        @DisplayName("should find by resource type only when codeSystem null but codeValue not")
        void shouldFindByResourceTypeWhenCodeSystemNull() {
            when(triggerIndexRepository.findByResourceType("Encounter"))
                    .thenReturn(Collections.emptyList());

            List<TriggerIndex> result = service.findStructuralMatches("Encounter", null, "12345-6");

            assertThat(result).isEmpty();
            verify(triggerIndexRepository).findByResourceType("Encounter");
        }

        @Test
        @DisplayName("should return empty when no matches found")
        void shouldReturnEmptyWhenNoMatches() {
            when(triggerIndexRepository.findByResourceTypeAndCode("Unknown", "sys", "code"))
                    .thenReturn(Collections.emptyList());

            List<TriggerIndex> result = service.findStructuralMatches("Unknown", "sys", "code");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("evaluateCondition")
    class EvaluateCondition {

        @Test
        @DisplayName("should return true when no condition expression")
        void shouldReturnTrueWhenNoCondition() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            action.setId("action-1");

            when(planDefinitionParser.extractConditionExpression(action)).thenReturn(null);

            boolean result = service.evaluateCondition(action, Map.of());

            assertThat(result).isTrue();
            verify(expressionEvaluationService, never()).evaluate(any(), any(), any());
        }

        @Test
        @DisplayName("should return true when condition expression is blank")
        void shouldReturnTrueWhenBlankExpression() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            action.setId("action-1");

            when(planDefinitionParser.extractConditionExpression(action)).thenReturn("   ");

            boolean result = service.evaluateCondition(action, Map.of());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should delegate to ExpressionEvaluationService")
        void shouldDelegateToEvaluationService() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            action.setId("action-1");

            String language = "text/jsonlogic";
            String expression = "{\"==\":[{\"var\":\"event.resourceType\"},\"Encounter\"]}";

            when(planDefinitionParser.extractConditionLanguage(action)).thenReturn(language);
            when(planDefinitionParser.extractConditionExpression(action)).thenReturn(expression);
            when(expressionEvaluationService.evaluate(language, expression, Map.of())).thenReturn(true);

            boolean result = service.evaluateCondition(action, Map.of());

            assertThat(result).isTrue();
            verify(expressionEvaluationService).evaluate(language, expression, Map.of());
        }

        @Test
        @DisplayName("should return false when condition evaluates to false")
        void shouldReturnFalseWhenConditionFalse() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            action.setId("action-1");

            when(planDefinitionParser.extractConditionLanguage(action)).thenReturn("text/jsonlogic");
            when(planDefinitionParser.extractConditionExpression(action)).thenReturn("{\"==\":[1,2]}");
            when(expressionEvaluationService.evaluate(any(), any(), any())).thenReturn(false);

            boolean result = service.evaluateCondition(action, Map.of());

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("findByPlanDefinitionId")
    class FindByPlanDefinitionId {

        @Test
        void shouldReturnTriggersForPlanDefinition() {
            UUID pdId = UUID.randomUUID();
            when(triggerIndexRepository.findByPlanDefinitionId(pdId))
                    .thenReturn(List.of(createTriggerIndex("Encounter", "", "")));

            assertThat(service.findByPlanDefinitionId(pdId)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("evaluateCondition with TriggerIndex (trigger-level)")
    class EvaluateConditionWithTriggerIndex {

        @Test
        @DisplayName("should evaluate trigger-level condition when present")
        void shouldEvaluateTriggerLevelCondition() {
            // Set up action with trigger that has a condition
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            action.setId("enrollment");

            TriggerIndex triggerMatch = createTriggerIndex("Encounter", "http://openphc.org/encounter-types", "VISIT_ENCOUNTER");

            org.hl7.fhir.r4.model.TriggerDefinition matchedTrigger = new org.hl7.fhir.r4.model.TriggerDefinition();

            when(planDefinitionParser.findMatchingTrigger(action, "Encounter",
                    "http://openphc.org/encounter-types", "VISIT_ENCOUNTER"))
                    .thenReturn(matchedTrigger);
            when(planDefinitionParser.extractTriggerConditionExpression(matchedTrigger))
                    .thenReturn("{\"!=\":[{\"var\":\"resource.status\"},\"finished\"]}");
            when(planDefinitionParser.extractTriggerConditionLanguage(matchedTrigger))
                    .thenReturn("text/jsonlogic");
            when(expressionEvaluationService.evaluate("text/jsonlogic",
                    "{\"!=\":[{\"var\":\"resource.status\"},\"finished\"]}", Map.of("resource", Map.of("status", "in-progress"))))
                    .thenReturn(true);
            // Action-level condition (none)
            when(planDefinitionParser.extractConditionExpression(action)).thenReturn(null);

            boolean result = service.evaluateCondition(action, triggerMatch,
                    Map.of("resource", Map.of("status", "in-progress")));

            assertThat(result).isTrue();
            verify(expressionEvaluationService).evaluate("text/jsonlogic",
                    "{\"!=\":[{\"var\":\"resource.status\"},\"finished\"]}",
                    Map.of("resource", Map.of("status", "in-progress")));
        }

        @Test
        @DisplayName("should reject when trigger-level condition evaluates to false")
        void shouldRejectWhenTriggerConditionFalse() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            action.setId("enrollment");

            TriggerIndex triggerMatch = createTriggerIndex("Encounter", "http://openphc.org/encounter-types", "VISIT_ENCOUNTER");

            org.hl7.fhir.r4.model.TriggerDefinition matchedTrigger = new org.hl7.fhir.r4.model.TriggerDefinition();

            when(planDefinitionParser.findMatchingTrigger(action, "Encounter",
                    "http://openphc.org/encounter-types", "VISIT_ENCOUNTER"))
                    .thenReturn(matchedTrigger);
            when(planDefinitionParser.extractTriggerConditionExpression(matchedTrigger))
                    .thenReturn("{\"!=\":[{\"var\":\"resource.status\"},\"finished\"]}");
            when(planDefinitionParser.extractTriggerConditionLanguage(matchedTrigger))
                    .thenReturn("text/jsonlogic");
            when(expressionEvaluationService.evaluate(any(), any(), any())).thenReturn(false);

            boolean result = service.evaluateCondition(action, triggerMatch, Map.of("resource", Map.of("status", "finished")));

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should fall through to action-level condition when no trigger condition")
        void shouldFallThroughToActionLevel() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            action.setId("action-1");

            TriggerIndex triggerMatch = createTriggerIndex("Encounter", "", "");

            org.hl7.fhir.r4.model.TriggerDefinition matchedTrigger = new org.hl7.fhir.r4.model.TriggerDefinition();

            when(planDefinitionParser.findMatchingTrigger(action, "Encounter", "", ""))
                    .thenReturn(matchedTrigger);
            when(planDefinitionParser.extractTriggerConditionExpression(matchedTrigger)).thenReturn(null);
            // Falls through to action-level
            when(planDefinitionParser.extractConditionExpression(action)).thenReturn("{\"==\":[1,1]}");
            when(planDefinitionParser.extractConditionLanguage(action)).thenReturn("text/jsonlogic");
            when(expressionEvaluationService.evaluate("text/jsonlogic", "{\"==\":[1,1]}", Map.of())).thenReturn(true);

            boolean result = service.evaluateCondition(action, triggerMatch, Map.of());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should auto-pass when no trigger or action conditions exist")
        void shouldAutoPassWhenNoConditions() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            action.setId("action-1");

            TriggerIndex triggerMatch = createTriggerIndex("Observation", "", "");

            when(planDefinitionParser.findMatchingTrigger(action, "Observation", "", ""))
                    .thenReturn(null);
            when(planDefinitionParser.extractConditionExpression(action)).thenReturn(null);

            boolean result = service.evaluateCondition(action, triggerMatch, Map.of());

            assertThat(result).isTrue();
            verify(expressionEvaluationService, never()).evaluate(any(), any(), any());
        }
    }
}
