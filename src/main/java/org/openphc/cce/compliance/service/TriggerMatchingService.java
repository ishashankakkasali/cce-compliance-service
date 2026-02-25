package org.openphc.cce.compliance.service;

import org.openphc.cce.compliance.domain.entity.TriggerIndex;
import org.openphc.cce.compliance.domain.repository.TriggerIndexRepository;
import org.openphc.cce.compliance.fhir.ExpressionEvaluationService;
import org.openphc.cce.compliance.fhir.PlanDefinitionParser;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Two-tier trigger matching service as described in CCE Solution Design.
 * <p>
 * <strong>Tier 1 — Structural Match:</strong>
 * Uses the trigger_index table to find PlanDefinition actions whose triggers
 * match an inbound event's resource type and code.
 * <p>
 * <strong>Tier 2 — Condition Evaluation:</strong>
 * For structurally matched actions, evaluates any JSONLogic conditions
 * against the event data and patient/protocol context.
 */
@Service
@Transactional(readOnly = true)
public class TriggerMatchingService {

    private static final Logger log = LoggerFactory.getLogger(TriggerMatchingService.class);

    private final TriggerIndexRepository triggerIndexRepository;
    private final ExpressionEvaluationService expressionEvaluationService;
    private final PlanDefinitionParser planDefinitionParser;

    public TriggerMatchingService(TriggerIndexRepository triggerIndexRepository,
                                   ExpressionEvaluationService expressionEvaluationService,
                                   PlanDefinitionParser planDefinitionParser) {
        this.triggerIndexRepository = triggerIndexRepository;
        this.expressionEvaluationService = expressionEvaluationService;
        this.planDefinitionParser = planDefinitionParser;
    }

    /**
     * Tier 1: Performs structural matching against the trigger index.
     * Finds all trigger index entries matching the given resource type and optional code.
     *
     * @param resourceType the FHIR resource type (e.g., "Encounter", "Observation")
     * @param codeSystem   the code system (may be null for unfiltered match)
     * @param codeValue    the code value (may be null for unfiltered match)
     * @return list of matching trigger index entries
     */
    public List<TriggerIndex> findStructuralMatches(String resourceType, String codeSystem, String codeValue) {
        if (codeSystem != null && codeValue != null) {
            List<TriggerIndex> matches = triggerIndexRepository
                    .findByResourceTypeAndCode(resourceType, codeSystem, codeValue);
            log.debug("Tier 1 structural match: resourceType={}, code={}/{}, matches={}",
                    resourceType, codeSystem, codeValue, matches.size());
            return matches;
        }

        List<TriggerIndex> matches = triggerIndexRepository.findByResourceType(resourceType);
        log.debug("Tier 1 structural match (resource-only): resourceType={}, matches={}",
                resourceType, matches.size());
        return matches;
    }

    /**
     * Tier 2: Evaluates the condition expression from a PlanDefinition action
     * against the event data and context variables.
     * <p>
     * Checks action-level conditions only. For trigger-level condition evaluation,
     * use {@link #evaluateCondition(PlanDefinition.PlanDefinitionActionComponent, TriggerIndex, Map)}.
     *
     * @param action    the PlanDefinition action component
     * @param variables the variable bindings for expression evaluation
     * @return true if no condition exists or if the condition evaluates to true
     */
    public boolean evaluateCondition(PlanDefinition.PlanDefinitionActionComponent action,
                                      Map<String, Object> variables) {
        String language = planDefinitionParser.extractConditionLanguage(action);
        String expression = planDefinitionParser.extractConditionExpression(action);

        if (expression == null || expression.isBlank()) {
            log.debug("No condition for action {}, treating as matched", action.getId());
            return true;
        }

        boolean result = expressionEvaluationService.evaluate(language, expression, variables);
        log.debug("Tier 2 condition evaluation: actionId={}, language={}, result={}",
                action.getId(), language, result);
        return result;
    }

    /**
     * Tier 2: Evaluates condition expressions at both trigger and action levels.
     * <p>
     * First finds the specific trigger within the action that produced the structural
     * match (using the TriggerIndex's resource type and code). If that trigger has a
     * condition (e.g., JSONLogic on {@code trigger[].condition}), it is evaluated first.
     * If the trigger-level condition passes (or does not exist), the action-level
     * condition is evaluated as a second gate.
     * <p>
     * This supports eBUZIMA-style protocols where conditions are placed on
     * {@code trigger[].condition} rather than {@code action.condition[]}.
     *
     * @param action       the PlanDefinition action component
     * @param triggerMatch the TriggerIndex entry from Tier 1 structural matching
     * @param variables    the variable bindings for expression evaluation
     * @return true if all applicable conditions evaluate to true
     */
    public boolean evaluateCondition(PlanDefinition.PlanDefinitionActionComponent action,
                                      TriggerIndex triggerMatch,
                                      Map<String, Object> variables) {
        // Step 1: Find the matching trigger within the action
        org.hl7.fhir.r4.model.TriggerDefinition matchedTrigger =
                planDefinitionParser.findMatchingTrigger(
                        action,
                        triggerMatch.getResourceType(),
                        triggerMatch.getCodeSystem(),
                        triggerMatch.getCodeValue());

        // Step 2: Evaluate trigger-level condition if present
        if (matchedTrigger != null) {
            String triggerExpression = planDefinitionParser.extractTriggerConditionExpression(matchedTrigger);
            if (triggerExpression != null && !triggerExpression.isBlank()) {
                String triggerLanguage = planDefinitionParser.extractTriggerConditionLanguage(matchedTrigger);
                boolean triggerResult = expressionEvaluationService.evaluate(
                        triggerLanguage, triggerExpression, variables);
                log.debug("Tier 2 trigger-level condition: actionId={}, language={}, result={}",
                        action.getId(), triggerLanguage, triggerResult);
                if (!triggerResult) {
                    return false;
                }
            }
        }

        // Step 3: Evaluate action-level condition as second gate
        return evaluateCondition(action, variables);
    }

    /**
     * Finds trigger index entries by PlanDefinition ID.
     */
    public List<TriggerIndex> findByPlanDefinitionId(java.util.UUID planDefinitionId) {
        return triggerIndexRepository.findByPlanDefinitionId(planDefinitionId);
    }
}
