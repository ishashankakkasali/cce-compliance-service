package org.openphc.cce.compliance.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpressionEvaluationService Tests")
class ExpressionEvaluationServiceTest {

    @Mock private CqlEvaluationEngine cqlEvaluationEngine;

    private ExpressionEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new ExpressionEvaluationService(new ObjectMapper(), cqlEvaluationEngine);
    }

    @Nested
    @DisplayName("evaluate - JSONLogic")
    class EvaluateJsonLogic {

        @Test
        @DisplayName("should evaluate simple equality to true")
        void shouldEvaluateEqualityTrue() {
            String expression = "{\"==\":[1,1]}";

            boolean result = service.evaluate("text/jsonlogic", expression, Map.of());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should evaluate simple equality to false")
        void shouldEvaluateEqualityFalse() {
            String expression = "{\"==\":[1,2]}";

            boolean result = service.evaluate("text/jsonlogic", expression, Map.of());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should evaluate with variable bindings")
        void shouldEvaluateWithVariables() {
            String expression = "{\"==\":[{\"var\":\"event.resourceType\"},\"Encounter\"]}";
            Map<String, Object> variables = Map.of(
                    "event", Map.of("resourceType", "Encounter")
            );

            boolean result = service.evaluate("text/jsonlogic", expression, variables);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should evaluate greater-than comparison")
        void shouldEvaluateGreaterThan() {
            String expression = "{\">\": [{\"var\": \"event.value\"}, 100]}";
            Map<String, Object> variables = Map.of(
                    "event", Map.of("value", 150)
            );

            boolean result = service.evaluate("text/jsonlogic", expression, variables);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should evaluate AND condition")
        void shouldEvaluateAndCondition() {
            String expression = "{\"and\":[{\"==\":[{\"var\":\"event.resourceType\"},\"Observation\"]},{\">\": [{\"var\": \"event.value\"}, 50]}]}";
            Map<String, Object> variables = Map.of(
                    "event", Map.of("resourceType", "Observation", "value", 75)
            );

            boolean result = service.evaluate("text/jsonlogic", expression, variables);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should throw ExpressionEvaluationException for invalid JSONLogic")
        void shouldThrowForInvalidJsonLogic() {
            String expression = "not-valid-json";

            assertThatThrownBy(() -> service.evaluate("text/jsonlogic", expression, Map.of()))
                    .isInstanceOf(ExpressionEvaluationService.ExpressionEvaluationException.class);
        }
    }

    @Nested
    @DisplayName("evaluate - CQL")
    class EvaluateCql {

        @Test
        @DisplayName("should delegate CQL evaluation to CqlEvaluationEngine")
        void shouldDelegateToCqlEngine() {
            String expression = "1 = 1";
            Map<String, Object> variables = Map.of("event", Map.of());

            when(cqlEvaluationEngine.evaluate(expression, variables)).thenReturn(true);

            boolean result = service.evaluate("text/cql", expression, variables);

            assertThat(result).isTrue();
            verify(cqlEvaluationEngine).evaluate(expression, variables);
        }
    }

    @Nested
    @DisplayName("evaluate - null/blank/unsupported")
    class EvaluateEdgeCases {

        @Test
        @DisplayName("should return true when language is null")
        void shouldReturnTrueWhenLanguageNull() {
            boolean result = service.evaluate(null, "{\"==\":[1,1]}", Map.of());
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when expression is null")
        void shouldReturnTrueWhenExpressionNull() {
            boolean result = service.evaluate("text/jsonlogic", null, Map.of());
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when expression is blank")
        void shouldReturnTrueWhenExpressionBlank() {
            boolean result = service.evaluate("text/jsonlogic", "  ", Map.of());
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for unsupported language")
        void shouldReturnTrueForUnsupportedLanguage() {
            boolean result = service.evaluate("text/fhirpath", "some expression", Map.of());
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("buildVariables")
    class BuildVariables {

        @Test
        @DisplayName("should build variables with all contexts")
        void shouldBuildWithAllContexts() {
            Map<String, Object> result = service.buildVariables(
                    Map.of("resourceType", "Encounter"),
                    Map.of("id", "Patient/123"),
                    Map.of("actionId", "action-1"),
                    Map.of("canonical", "http://example.org/pd|1.0")
            );

            assertThat(result).containsKeys("event", "patient", "step", "protocol");
            assertThat((Map<String, Object>) result.get("event")).containsEntry("resourceType", "Encounter");
            assertThat((Map<String, Object>) result.get("patient")).containsEntry("id", "Patient/123");
        }

        @Test
        @DisplayName("should use empty maps for null contexts")
        void shouldUseEmptyMapsForNulls() {
            Map<String, Object> result = service.buildVariables(null, null, null, null);

            assertThat(result).containsKeys("event", "patient", "step", "protocol");
            assertThat((Map<String, Object>) result.get("event")).isEmpty();
        }
    }
}
