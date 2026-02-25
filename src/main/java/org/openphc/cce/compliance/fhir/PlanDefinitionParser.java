package org.openphc.cce.compliance.fhir;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Parses and navigates FHIR R4 PlanDefinition resources.
 * <p>
 * Extracts trigger definitions, conditions, timing constraints, and action
 * hierarchies from PlanDefinition JSON stored in the database.
 */
@Component
public class PlanDefinitionParser {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(PlanDefinitionParser.class);

    private final IParser fhirJsonParser;

    public PlanDefinitionParser(IParser fhirJsonParser) {
        this.fhirJsonParser = fhirJsonParser;
    }

    /**
     * Parses a PlanDefinition from its JSON representation.
     *
     * @param json the FHIR PlanDefinition JSON string
     * @return the parsed PlanDefinition resource
     */
    public PlanDefinition parse(String json) {
        return fhirJsonParser.parseResource(PlanDefinition.class, json);
    }

    /**
     * Parses a PlanDefinition from a JSONB map (as stored in the database).
     *
     * @param definitionMap the definition JSONB map
     * @return the parsed PlanDefinition resource
     */
    public PlanDefinition parseFromMap(Map<String, Object> definitionMap) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(definitionMap);
            return fhirJsonParser.parseResource(PlanDefinition.class, json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize definition map", e);
        }
    }

    /**
     * Extracts all actions from a PlanDefinition, flattening nested actions.
     *
     * @param planDefinition the PlanDefinition resource
     * @return a flat list of all actions in traversal order
     */
    public List<PlanDefinition.PlanDefinitionActionComponent> extractAllActions(PlanDefinition planDefinition) {
        List<PlanDefinition.PlanDefinitionActionComponent> allActions = new ArrayList<>();
        if (planDefinition.hasAction()) {
            for (PlanDefinition.PlanDefinitionActionComponent action : planDefinition.getAction()) {
                flattenActions(action, allActions);
            }
        }
        return allActions;
    }

    private void flattenActions(PlanDefinition.PlanDefinitionActionComponent action,
                                List<PlanDefinition.PlanDefinitionActionComponent> accumulator) {
        accumulator.add(action);
        if (action.hasAction()) {
            for (PlanDefinition.PlanDefinitionActionComponent child : action.getAction()) {
                flattenActions(child, accumulator);
            }
        }
    }

    /**
     * Extracts trigger definitions from a specific action.
     *
     * @param action the PlanDefinition action
     * @return list of trigger definitions
     */
    public List<TriggerDefinition> extractTriggers(PlanDefinition.PlanDefinitionActionComponent action) {
        if (action.hasTrigger()) {
            return action.getTrigger();
        }
        return Collections.emptyList();
    }

    /**
     * Extracts the resource type from a trigger definition's data requirement.
     *
     * @param trigger the trigger definition
     * @return the resource type string, or null if not available
     */
    public String extractResourceType(TriggerDefinition trigger) {
        if (trigger.hasData() && !trigger.getData().isEmpty()) {
            DataRequirement dataReq = trigger.getData().get(0);
            if (dataReq.hasType()) {
                return dataReq.getType();
            }
        }
        return null;
    }

    /**
     * Extracts code filter values from a trigger definition's data requirement.
     *
     * @param trigger the trigger definition
     * @return list of code-system/code-value pairs
     */
    public List<CodeFilter> extractCodeFilters(TriggerDefinition trigger) {
        List<CodeFilter> result = new ArrayList<>();
        if (trigger.hasData() && !trigger.getData().isEmpty()) {
            DataRequirement dataReq = trigger.getData().get(0);
            if (dataReq.hasCodeFilter()) {
                for (DataRequirement.DataRequirementCodeFilterComponent codeFilter : dataReq.getCodeFilter()) {
                    if (codeFilter.hasCode()) {
                        for (Coding coding : codeFilter.getCode()) {
                            result.add(new CodeFilter(coding.getSystem(), coding.getCode()));
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Extracts the condition expression from an action's condition.
     *
     * @param action the PlanDefinition action
     * @return the condition expression, or null if none
     */
    public String extractConditionExpression(PlanDefinition.PlanDefinitionActionComponent action) {
        if (action.hasCondition()) {
            for (var condition : action.getCondition()) {
                if (condition.hasExpression() && condition.getExpression().hasExpression()) {
                    return condition.getExpression().getExpression();
                }
            }
        }
        return null;
    }

    /**
     * Extracts the condition language from an action's condition.
     *
     * @param action the PlanDefinition action
     * @return the condition language (e.g., "text/jsonlogic", "text/fhirpath"), or null
     */
    public String extractConditionLanguage(PlanDefinition.PlanDefinitionActionComponent action) {
        if (action.hasCondition()) {
            for (var condition : action.getCondition()) {
                if (condition.hasExpression() && condition.getExpression().hasLanguage()) {
                    return condition.getExpression().getLanguage();
                }
            }
        }
        return null;
    }

    /**
     * Extracts the condition expression from a trigger definition's condition.
     * <p>
     * In FHIR R4, {@code TriggerDefinition.condition} is a single {@code Expression}
     * (unlike action-level conditions which are a list). This is where eBUZIMA-style
     * protocols place their JSONLogic filtering expressions.
     *
     * @param trigger the trigger definition
     * @return the condition expression, or null if none
     */
    public String extractTriggerConditionExpression(TriggerDefinition trigger) {
        if (trigger.hasCondition() && trigger.getCondition().hasExpression()) {
            return trigger.getCondition().getExpression();
        }
        return null;
    }

    /**
     * Extracts the condition language from a trigger definition's condition.
     *
     * @param trigger the trigger definition
     * @return the condition language (e.g., "text/jsonlogic"), or null
     */
    public String extractTriggerConditionLanguage(TriggerDefinition trigger) {
        if (trigger.hasCondition() && trigger.getCondition().hasLanguage()) {
            return trigger.getCondition().getLanguage();
        }
        return null;
    }

    /**
     * Finds the trigger definition within an action that matches the given structural
     * criteria (resource type and optional code). Used during Tier 2 evaluation to
     * locate the specific trigger whose condition should be evaluated.
     *
     * @param action       the PlanDefinition action
     * @param resourceType the resource type to match
     * @param codeSystem   the code system (may be null/empty)
     * @param codeValue    the code value (may be null/empty)
     * @return the matching trigger definition, or null if not found
     */
    public TriggerDefinition findMatchingTrigger(PlanDefinition.PlanDefinitionActionComponent action,
                                                  String resourceType,
                                                  String codeSystem,
                                                  String codeValue) {
        if (!action.hasTrigger()) return null;

        for (TriggerDefinition trigger : action.getTrigger()) {
            String triggerResourceType = extractResourceType(trigger);
            if (!Objects.equals(resourceType, triggerResourceType)) continue;

            // If no code filter on the trigger index entry, match by resource type alone
            if (codeSystem == null || codeSystem.isEmpty()) {
                List<CodeFilter> filters = extractCodeFilters(trigger);
                if (filters.isEmpty()) {
                    return trigger;
                }
                continue;
            }

            // Match against code filters
            List<CodeFilter> filters = extractCodeFilters(trigger);
            if (filters.isEmpty()) {
                // Trigger has no code filter — matches any code
                return trigger;
            }
            for (CodeFilter cf : filters) {
                if (Objects.equals(cf.system(), codeSystem) && Objects.equals(cf.code(), codeValue)) {
                    return trigger;
                }
            }
        }
        return null;
    }

    /**
     * Extracts timing information from an action.
     *
     * @param action the PlanDefinition action
     * @return a map of timing properties (offset, duration, etc.)
     */
    public Map<String, Object> extractTiming(PlanDefinition.PlanDefinitionActionComponent action) {
        Map<String, Object> timing = new LinkedHashMap<>();

        if (action.hasTimingTiming()) {
            Timing t = action.getTimingTiming();
            if (t.hasRepeat()) {
                Timing.TimingRepeatComponent repeat = t.getRepeat();
                if (repeat.hasDuration()) {
                    timing.put("duration", repeat.getDuration());
                    timing.put("durationUnit", repeat.getDurationUnit() != null
                            ? repeat.getDurationUnit().toCode() : null);
                }
                if (repeat.hasFrequency()) {
                    timing.put("frequency", repeat.getFrequency());
                }
                if (repeat.hasPeriod()) {
                    timing.put("period", repeat.getPeriod());
                    timing.put("periodUnit", repeat.getPeriodUnit() != null
                            ? repeat.getPeriodUnit().toCode() : null);
                }
            }
        }

        // Check for timing offsets in related actions
        if (action.hasRelatedAction()) {
            for (var related : action.getRelatedAction()) {
                if (related.hasOffsetDuration()) {
                    Duration offset = related.getOffsetDuration();
                    timing.put("offsetValue", offset.getValue());
                    timing.put("offsetUnit", offset.getUnit());
                }
            }
        }

        return timing;
    }

    // ==================== CCE Extension Extraction ====================

    private static final String EXT_TOLERANCE_DAYS = "http://openphc.org/fhir/StructureDefinition/cce-tolerance-days";
    private static final String EXT_INTELLIGENCE_SEVERITY = "http://openphc.org/fhir/StructureDefinition/cce-intelligence-severity";
    private static final String EXT_INTELLIGENCE_TARGET = "http://openphc.org/fhir/StructureDefinition/cce-intelligence-target";

    /**
     * Extracts the CCE tolerance-days extension value from an action.
     * Tolerance days define how many days after the due date before a step becomes overdue.
     *
     * @param action the PlanDefinition action
     * @return the tolerance days, or null if not specified
     */
    public Integer extractToleranceDays(PlanDefinition.PlanDefinitionActionComponent action) {
        Extension ext = action.getExtensionByUrl(EXT_TOLERANCE_DAYS);
        if (ext != null && ext.hasValue() && ext.getValue() instanceof org.hl7.fhir.r4.model.IntegerType intVal) {
            return intVal.getValue();
        }
        return null;
    }

    /**
     * Extracts the CCE intelligence-severity extension value from an action.
     * Defines the severity level of intelligence rule alerts (e.g., "warning", "error", "info").
     *
     * @param action the PlanDefinition action
     * @return the severity string, or null if not specified
     */
    public String extractIntelligenceSeverity(PlanDefinition.PlanDefinitionActionComponent action) {
        Extension ext = action.getExtensionByUrl(EXT_INTELLIGENCE_SEVERITY);
        if (ext != null && ext.hasValue() && ext.getValue() instanceof org.hl7.fhir.r4.model.CodeType codeVal) {
            return codeVal.getCode();
        }
        if (ext != null && ext.hasValue() && ext.getValue() instanceof org.hl7.fhir.r4.model.StringType strVal) {
            return strVal.getValue();
        }
        return null;
    }

    /**
     * Extracts the CCE intelligence-target extension value from an action.
     * Defines the target audience for the intelligence output (e.g., "provider", "patient", "supervisor").
     *
     * @param action the PlanDefinition action
     * @return the target string, or null if not specified
     */
    public String extractIntelligenceTarget(PlanDefinition.PlanDefinitionActionComponent action) {
        Extension ext = action.getExtensionByUrl(EXT_INTELLIGENCE_TARGET);
        if (ext != null && ext.hasValue() && ext.getValue() instanceof org.hl7.fhir.r4.model.CodeType codeVal) {
            return codeVal.getCode();
        }
        if (ext != null && ext.hasValue() && ext.getValue() instanceof org.hl7.fhir.r4.model.StringType strVal) {
            return strVal.getValue();
        }
        return null;
    }

    /**
     * Extracts the requiredBehavior from an action.
     * Determines if the step is must/should/could (optional).
     *
     * @param action the PlanDefinition action
     * @return the requiredBehavior code (e.g., "must", "could", "must-unless-documented"), or null
     */
    public String extractRequiredBehavior(PlanDefinition.PlanDefinitionActionComponent action) {
        if (action.hasRequiredBehavior()) {
            return action.getRequiredBehavior().toCode();
        }
        return null;
    }

    /**
     * Extracts the definitionCanonical from an action.
     * References an ActivityDefinition or child PlanDefinition.
     *
     * @param action the PlanDefinition action
     * @return the canonical reference, or null
     */
    public String extractDefinitionCanonical(PlanDefinition.PlanDefinitionActionComponent action) {
        if (action.hasDefinitionCanonicalType()) {
            return action.getDefinitionCanonicalType().getValue();
        }
        return null;
    }

    /**
     * Determines if an action is an intelligence rule.
     * Intelligence rules are nested sub-actions that have conditions but no triggers,
     * and typically include a definitionCanonical or CCE intelligence extensions.
     *
     * @param action the PlanDefinition action
     * @return true if the action appears to be an intelligence rule
     */
    public boolean isIntelligenceRule(PlanDefinition.PlanDefinitionActionComponent action) {
        boolean hasCondition = action.hasCondition();
        boolean hasTrigger = action.hasTrigger();
        boolean hasIntelligenceExt = action.hasExtension(EXT_INTELLIGENCE_SEVERITY)
                || action.hasExtension(EXT_INTELLIGENCE_TARGET);
        // Intelligence rules: have condition + no trigger, or have intelligence extensions
        return (hasCondition && !hasTrigger) || hasIntelligenceExt;
    }

    /**
     * Finds all actions that depend on the given action ID via relatedAction.
     * These are downstream actions (e.g., relationship = "after-end" or "after").
     *
     * @param allActions all flattened actions from the PlanDefinition
     * @param actionId   the action ID to find dependents for
     * @return list of dependent actions
     */
    public List<PlanDefinition.PlanDefinitionActionComponent> findDependentActions(
            List<PlanDefinition.PlanDefinitionActionComponent> allActions, String actionId) {
        List<PlanDefinition.PlanDefinitionActionComponent> dependents = new ArrayList<>();
        for (PlanDefinition.PlanDefinitionActionComponent action : allActions) {
            if (action.hasRelatedAction()) {
                for (var related : action.getRelatedAction()) {
                    if (actionId.equals(related.getActionId())) {
                        dependents.add(action);
                        break;
                    }
                }
            }
        }
        return dependents;
    }

    /**
     * Computes the due date offset from relatedAction.offsetDuration.
     * Returns the offset as a Java Duration, or null if no offset is defined.
     *
     * @param action the dependent action whose relatedAction has the offset
     * @param precedingActionId the action ID of the preceding (completed) action
     * @return the offset as java.time.Duration, or null
     */
    public java.time.Duration computeRelatedActionOffset(
            PlanDefinition.PlanDefinitionActionComponent action, String precedingActionId) {
        if (!action.hasRelatedAction()) return null;
        for (var related : action.getRelatedAction()) {
            if (precedingActionId.equals(related.getActionId()) && related.hasOffsetDuration()) {
                Duration offset = related.getOffsetDuration();
                if (offset.hasValue() && offset.hasUnit()) {
                    return convertFhirDurationToJava(offset.getValue(), offset.getUnit());
                }
            }
        }
        return null;
    }

    /**
     * Converts a FHIR Duration value + unit to a Java Duration.
     */
    private java.time.Duration convertFhirDurationToJava(java.math.BigDecimal value, String unit) {
        long amount = value.longValue();
        return switch (unit.toLowerCase()) {
            case "s", "sec", "second", "seconds" -> java.time.Duration.ofSeconds(amount);
            case "min", "minute", "minutes" -> java.time.Duration.ofMinutes(amount);
            case "h", "hour", "hours" -> java.time.Duration.ofHours(amount);
            case "d", "day", "days" -> java.time.Duration.ofDays(amount);
            case "wk", "week", "weeks" -> java.time.Duration.ofDays(amount * 7);
            case "mo", "month", "months" -> java.time.Duration.ofDays(amount * 30);
            case "a", "year", "years" -> java.time.Duration.ofDays(amount * 365);
            default -> {
                log.warn("Unknown FHIR duration unit: {}, defaulting to days", unit);
                yield java.time.Duration.ofDays(amount);
            }
        };
    }

    /**
     * Represents a code system + value pair from a FHIR DataRequirement code filter.
     */
    public record CodeFilter(String system, String code) {
    }
}
