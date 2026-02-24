package org.openphc.cce.compliance.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PlanDefinitionParser Tests")
class PlanDefinitionParserTest {

    private PlanDefinitionParser parser;

    @BeforeEach
    void setUp() {
        FhirContext ctx = FhirContext.forR4();
        parser = new PlanDefinitionParser(ctx.newJsonParser());
    }

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("should parse a valid PlanDefinition JSON")
        void shouldParseValidJson() {
            String json = """
                    {
                      "resourceType": "PlanDefinition",
                      "url": "http://example.org/pd/immunization-schedule",
                      "version": "1.0",
                      "status": "active",
                      "title": "Immunization Schedule"
                    }
                    """;

            PlanDefinition pd = parser.parse(json);

            assertThat(pd.getUrl()).isEqualTo("http://example.org/pd/immunization-schedule");
            assertThat(pd.getVersion()).isEqualTo("1.0");
            assertThat(pd.getStatus()).isEqualTo(Enumerations.PublicationStatus.ACTIVE);
            assertThat(pd.getTitle()).isEqualTo("Immunization Schedule");
        }

        @Test
        @DisplayName("should throw for invalid JSON")
        void shouldThrowForInvalidJson() {
            assertThatThrownBy(() -> parser.parse("not-valid-json"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("extractAllActions")
    class ExtractAllActions {

        @Test
        @DisplayName("should extract top-level actions")
        void shouldExtractTopLevelActions() {
            PlanDefinition pd = new PlanDefinition();
            PlanDefinition.PlanDefinitionActionComponent action1 = pd.addAction();
            action1.setId("action-1");
            action1.setTitle("First Action");
            PlanDefinition.PlanDefinitionActionComponent action2 = pd.addAction();
            action2.setId("action-2");

            List<PlanDefinition.PlanDefinitionActionComponent> result = parser.extractAllActions(pd);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("action-1");
        }

        @Test
        @DisplayName("should flatten nested actions")
        void shouldFlattenNestedActions() {
            PlanDefinition pd = new PlanDefinition();
            PlanDefinition.PlanDefinitionActionComponent parent = pd.addAction();
            parent.setId("parent");
            PlanDefinition.PlanDefinitionActionComponent child = parent.addAction();
            child.setId("child");
            PlanDefinition.PlanDefinitionActionComponent grandchild = child.addAction();
            grandchild.setId("grandchild");

            List<PlanDefinition.PlanDefinitionActionComponent> result = parser.extractAllActions(pd);

            assertThat(result).hasSize(3);
            assertThat(result.stream().map(PlanDefinition.PlanDefinitionActionComponent::getId).toList())
                    .containsExactly("parent", "child", "grandchild");
        }

        @Test
        @DisplayName("should return empty for PlanDefinition with no actions")
        void shouldReturnEmptyForNoActions() {
            PlanDefinition pd = new PlanDefinition();
            assertThat(parser.extractAllActions(pd)).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractTriggers")
    class ExtractTriggers {

        @Test
        @DisplayName("should extract triggers from action")
        void shouldExtractTriggers() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            TriggerDefinition trigger = action.addTrigger();
            trigger.setType(TriggerDefinition.TriggerType.DATAADDED);

            List<TriggerDefinition> result = parser.extractTriggers(action);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo(TriggerDefinition.TriggerType.DATAADDED);
        }

        @Test
        @DisplayName("should return empty when no triggers")
        void shouldReturnEmptyWhenNoTriggers() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            assertThat(parser.extractTriggers(action)).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractResourceType")
    class ExtractResourceType {

        @Test
        @DisplayName("should extract resource type from trigger")
        void shouldExtractResourceType() {
            TriggerDefinition trigger = new TriggerDefinition();
            DataRequirement dr = new DataRequirement();
            dr.setType("Encounter");
            trigger.addData(dr);

            String result = parser.extractResourceType(trigger);

            assertThat(result).isEqualTo("Encounter");
        }

        @Test
        @DisplayName("should return null when no data requirements")
        void shouldReturnNullWhenNoData() {
            TriggerDefinition trigger = new TriggerDefinition();
            assertThat(parser.extractResourceType(trigger)).isNull();
        }
    }

    @Nested
    @DisplayName("extractCodeFilters")
    class ExtractCodeFilters {

        @Test
        @DisplayName("should extract code filters from trigger")
        void shouldExtractCodeFilters() {
            TriggerDefinition trigger = new TriggerDefinition();
            DataRequirement dr = new DataRequirement();
            DataRequirement.DataRequirementCodeFilterComponent cf = dr.addCodeFilter();
            cf.addCode().setSystem("http://loinc.org").setCode("12345-6");
            cf.addCode().setSystem("http://snomed.info/sct").setCode("386661006");
            trigger.addData(dr);

            List<PlanDefinitionParser.CodeFilter> result = parser.extractCodeFilters(trigger);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).system()).isEqualTo("http://loinc.org");
            assertThat(result.get(0).code()).isEqualTo("12345-6");
            assertThat(result.get(1).system()).isEqualTo("http://snomed.info/sct");
        }

        @Test
        @DisplayName("should return empty when no code filters")
        void shouldReturnEmptyWhenNoCodeFilters() {
            TriggerDefinition trigger = new TriggerDefinition();
            trigger.addData(new DataRequirement());

            assertThat(parser.extractCodeFilters(trigger)).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractConditionExpression / extractConditionLanguage")
    class ExtractCondition {

        @Test
        @DisplayName("should extract condition expression")
        void shouldExtractConditionExpression() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            PlanDefinition.PlanDefinitionActionConditionComponent condition = action.addCondition();
            condition.setKind(PlanDefinition.ActionConditionKind.APPLICABILITY);
            Expression expr = new Expression();
            expr.setLanguage("text/jsonlogic");
            expr.setExpression("{\"==\":[1,1]}");
            condition.setExpression(expr);

            assertThat(parser.extractConditionExpression(action)).isEqualTo("{\"==\":[1,1]}");
            assertThat(parser.extractConditionLanguage(action)).isEqualTo("text/jsonlogic");
        }

        @Test
        @DisplayName("should return null when no condition")
        void shouldReturnNullWhenNoCondition() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();

            assertThat(parser.extractConditionExpression(action)).isNull();
            assertThat(parser.extractConditionLanguage(action)).isNull();
        }
    }

    @Nested
    @DisplayName("extractTiming")
    class ExtractTiming {

        @Test
        @DisplayName("should extract timing with duration")
        void shouldExtractTimingWithDuration() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            Timing timing = new Timing();
            Timing.TimingRepeatComponent repeat = timing.getRepeat();
            repeat.setDuration(7);
            repeat.setDurationUnit(Timing.UnitsOfTime.D);
            repeat.setFrequency(1);
            action.setTiming(timing);

            Map<String, Object> result = parser.extractTiming(action);

            assertThat(result).containsEntry("durationUnit", "d");
            assertThat(result).containsKey("duration");
        }

        @Test
        @DisplayName("should return empty map when no timing")
        void shouldReturnEmptyWhenNoTiming() {
            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            assertThat(parser.extractTiming(action)).isEmpty();
        }
    }
}
