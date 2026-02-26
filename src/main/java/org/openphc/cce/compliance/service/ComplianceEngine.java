package org.openphc.cce.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.openphc.cce.compliance.domain.entity.*;
import org.openphc.cce.compliance.domain.enums.*;
import org.openphc.cce.compliance.fhir.ExpressionEvaluationService;
import org.openphc.cce.compliance.fhir.PlanDefinitionParser;
import org.openphc.cce.compliance.kafka.model.CloudEventMessage;
import org.openphc.cce.compliance.kafka.producer.DeadLetterProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The CCE Compliance Engine — central orchestrator for inbound event processing.
 * <p>
 * Implements the core compliance matching pipeline:
 * <ol>
 *   <li>Idempotency check (deduplicate by CloudEvents ID + source)</li>
 *   <li>Tier 1 structural matching via trigger index</li>
 *   <li>Tier 2 condition evaluation (JSONLogic)</li>
 *   <li>Protocol instance enrollment (auto-enroll if needed)</li>
 *   <li>Step instance completion</li>
 *   <li>Event log recording</li>
 * </ol>
 *
 * @see TriggerMatchingService
 * @see ExpressionEvaluationService
 */
@Service
@Transactional
public class ComplianceEngine {

    private static final Logger log = LoggerFactory.getLogger(ComplianceEngine.class);

    private final EventLogService eventLogService;
    private final TriggerMatchingService triggerMatchingService;
    private final ProtocolDefinitionService protocolDefinitionService;
    private final ProtocolInstanceService protocolInstanceService;
    private final StepInstanceService stepInstanceService;
    private final DeviationService deviationService;
    private final AuditService auditService;
    private final DeadLetterProducer deadLetterProducer;
    private final PlanDefinitionParser planDefinitionParser;
    private final ExpressionEvaluationService expressionEvaluationService;
    private final ObjectMapper objectMapper;

    // Observability
    private final Counter eventsProcessedCounter;
    private final Counter eventsMatchedCounter;
    private final Counter eventsZeroMatchCounter;
    private final Counter eventsAmbiguousCounter;
    private final Counter eventsDuplicateCounter;
    private final Timer matchingDurationTimer;

    public ComplianceEngine(EventLogService eventLogService,
                             TriggerMatchingService triggerMatchingService,
                             ProtocolDefinitionService protocolDefinitionService,
                             ProtocolInstanceService protocolInstanceService,
                             StepInstanceService stepInstanceService,
                             DeviationService deviationService,
                             AuditService auditService,
                             DeadLetterProducer deadLetterProducer,
                             PlanDefinitionParser planDefinitionParser,
                             ExpressionEvaluationService expressionEvaluationService,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.eventLogService = eventLogService;
        this.triggerMatchingService = triggerMatchingService;
        this.protocolDefinitionService = protocolDefinitionService;
        this.protocolInstanceService = protocolInstanceService;
        this.stepInstanceService = stepInstanceService;
        this.deviationService = deviationService;
        this.auditService = auditService;
        this.deadLetterProducer = deadLetterProducer;
        this.planDefinitionParser = planDefinitionParser;
        this.expressionEvaluationService = expressionEvaluationService;
        this.objectMapper = objectMapper;

        this.eventsProcessedCounter = Counter.builder("cce.events.processed")
                .description("Total events processed by the compliance engine")
                .register(meterRegistry);
        this.eventsMatchedCounter = Counter.builder("cce.events.matched")
                .tag("status", "matched")
                .description("Events that matched at least one step")
                .register(meterRegistry);
        this.eventsZeroMatchCounter = Counter.builder("cce.events.matched")
                .tag("status", "zero_match")
                .description("Events with zero trigger matches")
                .register(meterRegistry);
        this.eventsAmbiguousCounter = Counter.builder("cce.events.matched")
                .tag("status", "ambiguous")
                .description("Events with ambiguous matches")
                .register(meterRegistry);
        this.eventsDuplicateCounter = Counter.builder("cce.events.duplicate")
                .description("Duplicate events rejected")
                .register(meterRegistry);
        this.matchingDurationTimer = Timer.builder("cce.step.matching.duration")
                .description("Duration of the step matching pipeline")
                .register(meterRegistry);
    }

    /**
     * Processes an inbound CloudEvent through the compliance matching pipeline.
     *
     * @param event the inbound CloudEvent message
     */
    public void processInboundEvent(CloudEventMessage event) {
        eventsProcessedCounter.increment();

        // Step 1: Idempotency check
        if (eventLogService.isDuplicate(event.getId(), event.getSource())) {
            log.info("Duplicate event detected, skipping: id={}, source={}", event.getId(), event.getSource());
            eventsDuplicateCounter.increment();
            return;
        }

        // Step 2: Record event in event log (initial status)
        EventLog eventLog = eventLogService.recordEvent(event, ProcessingStatus.ZERO_MATCH);

        Timer.Sample matchingSample = Timer.start();
        try {
            // Step 2a: Explicit matching — if event carries actionId, bypass structural matching
            if (event.getActionId() != null && !event.getActionId().isBlank()) {
                boolean handled = processExplicitMatch(event, eventLog);
                if (handled) return;
                // If explicit match fails (PD not found, etc.), fall through to structural matching
                log.warn("Explicit match failed for actionId={}, falling through to structural match",
                        event.getActionId());
            }

            // Step 3: Extract resource type and codes from event
            String resourceType = extractResourceType(event);
            String patientId = event.getSubject();

            if (resourceType == null) {
                log.warn("Cannot determine resource type from event: id={}", event.getId());
                return;
            }

            // Step 4: Tier 1 — Structural matching via all-code-filters-must-match
            List<CodePair> allCodes = extractAllCodes(event);
            List<TriggerIndex> structuralMatches;

            // Fetch ALL trigger index entries for this resource type
            List<TriggerIndex> allEntries = triggerMatchingService
                    .findStructuralMatches(resourceType, null, null);

            if (allCodes.isEmpty()) {
                // No codes extracted — return all resource-type matches
                structuralMatches = allEntries;
            } else {
                // Build a set of the event's code keys for fast lookup
                Set<String> eventCodeKeys = allCodes.stream()
                        .map(cp -> cp.system() + "|" + cp.code())
                        .collect(Collectors.toSet());

                // Group trigger index entries by (planDefinitionId + actionId)
                // to avoid merging code filters from different protocols with same action name
                Map<String, List<TriggerIndex>> entriesByAction = allEntries.stream()
                        .collect(Collectors.groupingBy(
                                ti -> ti.getPlanDefinitionId() + ":" + ti.getActionId()));

                // For each action, check if ALL its distinct code filters are satisfied
                Set<TriggerIndex> matchSet = new LinkedHashSet<>();
                for (Map.Entry<String, List<TriggerIndex>> entry : entriesByAction.entrySet()) {
                    List<TriggerIndex> actionEntries = entry.getValue();

                    // Collect distinct code keys for this action, skipping wildcard entries
                    Set<String> actionCodeKeys = actionEntries.stream()
                            .map(ti -> ti.getCodeSystem() + "|" + ti.getCodeValue())
                            .filter(key -> !key.equals("|"))  // skip ("","") wildcards
                            .collect(Collectors.toSet());

                    // Action matches if ALL its code filters exist in the event's codes
                    // (an action with no code filters is a wildcard — always matches)
                    if (actionCodeKeys.isEmpty() || eventCodeKeys.containsAll(actionCodeKeys)) {
                        // Add only ONE representative entry per action to avoid duplicate matches
                        matchSet.add(actionEntries.get(0));
                    }
                }
                structuralMatches = new ArrayList<>(matchSet);
            }

            if (structuralMatches.isEmpty()) {
                log.info("No structural matches for event: id={}, resourceType={}, codes={}",
                        event.getId(), resourceType, allCodes);
                eventsZeroMatchCounter.increment();
                return;
            }

            // Step 5: Tier 2 — Condition evaluation and step matching
            List<MatchResult> confirmedMatches = new ArrayList<>();

            for (TriggerIndex triggerMatch : structuralMatches) {
                Optional<PlanDefinitionEntity> pdOpt = protocolDefinitionService.findById(triggerMatch.getPlanDefinitionId());
                if (pdOpt.isEmpty() || pdOpt.get().getStatus() != PlanDefinitionStatus.ACTIVE) {
                    continue;
                }

                PlanDefinitionEntity pdEntity = pdOpt.get();
                try {
                    String pdJson = objectMapper.writeValueAsString(pdEntity.getDefinition());
                    PlanDefinition pd = planDefinitionParser.parse(pdJson);

                    // Find the matching action
                    Optional<PlanDefinition.PlanDefinitionActionComponent> actionOpt =
                            planDefinitionParser.extractAllActions(pd).stream()
                                    .filter(a -> triggerMatch.getActionId().equals(a.getId()))
                                    .findFirst();

                    if (actionOpt.isPresent()) {
                        PlanDefinition.PlanDefinitionActionComponent action = actionOpt.get();

                        // Build variable context for condition evaluation
                        Map<String, Object> variables = expressionEvaluationService.buildVariables(
                                event.getData(), Map.of("id", patientId != null ? patientId : ""),
                                Map.of(), Map.of());

                        // Evaluate Tier 2 condition (trigger-level + action-level)
                        if (triggerMatchingService.evaluateCondition(action, triggerMatch, variables)) {
                            confirmedMatches.add(new MatchResult(pdEntity, triggerMatch.getActionId(), action));
                        }
                    }
                } catch (Exception ex) {
                    log.error("Error evaluating condition for PD {} action {}: {}",
                            pdEntity.getId(), triggerMatch.getActionId(), ex.getMessage(), ex);
                }
            }

            // Step 6: Process matches — group by protocol to support multi-protocol matching.
            // A single event may match one action per protocol. Ambiguity only applies
            // when a protocol has multiple matches (intra-protocol conflict).
            if (confirmedMatches.isEmpty()) {
                eventsZeroMatchCounter.increment();
                log.info("No confirmed matches after Tier 2 evaluation for event: id={}", event.getId());

            } else {
                // Group matches by protocol (PlanDefinition ID)
                Map<UUID, List<MatchResult>> matchesByProtocol = confirmedMatches.stream()
                        .collect(Collectors.groupingBy(m -> m.planDefinition().getId()));

                boolean anyAmbiguous = false;
                List<MatchResult> processableMatches = new ArrayList<>();

                for (Map.Entry<UUID, List<MatchResult>> protocolEntry : matchesByProtocol.entrySet()) {
                    List<MatchResult> protocolMatches = protocolEntry.getValue();

                    if (protocolMatches.size() == 1) {
                        // Single match in this protocol — process it
                        processableMatches.add(protocolMatches.get(0));
                    } else {
                        // Intra-protocol ambiguity — flag and record deviations
                        anyAmbiguous = true;
                        for (MatchResult match : protocolMatches) {
                            ProtocolInstance pi = protocolInstanceService.enrollOrGetActive(patientId, match.planDefinition());
                            List<StepInstance> activeSteps = stepInstanceService
                                    .findByProtocolInstanceIdAndState(pi.getId(), StepState.DUE);
                            if (!activeSteps.isEmpty()) {
                                deviationService.recordDeviation(pi, activeSteps.get(0), DeviationType.AMBIGUOUS,
                                        Map.of("matchCount", protocolMatches.size(),
                                                "protocolId", protocolEntry.getKey().toString(),
                                                "eventId", event.getId()));
                            }
                        }
                        log.warn("Intra-protocol ambiguity: event id={} matched {} actions in protocol {}",
                                event.getId(), protocolMatches.size(), protocolEntry.getKey());
                    }
                }

                if (!processableMatches.isEmpty()) {
                    // Process each protocol's single match
                    for (MatchResult match : processableMatches) {
                        processMatch(event, eventLog, match, patientId);
                    }
                    eventsMatchedCounter.increment();
                } else if (anyAmbiguous) {
                    eventsAmbiguousCounter.increment();
                    eventLogService.updateMatchResult(eventLog.getId(), null, null, null, null,
                            ProcessingStatus.AMBIGUOUS);
                    log.warn("Ambiguous match: event id={} — all protocols had intra-protocol conflicts",
                            event.getId());
                }
            }

        } catch (Exception ex) {
            log.error("Compliance engine processing error for event: id={}", event.getId(), ex);
            deadLetterProducer.publishDeadLetter(event, ex.getMessage(),
                    FailureStage.PROCESSING, event.getCorrelationId());
            throw ex;
        } finally {
            matchingSample.stop(matchingDurationTimer);
        }
    }

    /**
     * Processes a confirmed single match — enrolls patient, computes due dates,
     * completes step, triggers progressive instantiation, and evaluates intelligence rules.
     */
    private void processMatch(CloudEventMessage event, EventLog eventLog,
                               MatchResult match, String patientId) {
        // Enroll patient (or get existing active instance)
        ProtocolInstance protocolInstance = protocolInstanceService
                .enrollOrGetActive(patientId, match.planDefinition());

        // Compute due dates from timing/relatedAction offset and tolerance-days extension
        OffsetDateTime dueDate = null;
        OffsetDateTime overdueDate = null;
        OffsetDateTime missedDate = null;

        Map<String, Object> timing = planDefinitionParser.extractTiming(match.action());
        if (timing.containsKey("offsetValue")) {
            Object offsetVal = timing.get("offsetValue");
            String offsetUnit = (String) timing.get("offsetUnit");
            if (offsetVal != null && offsetUnit != null) {
                java.time.Duration offset = convertTimingOffset(offsetVal, offsetUnit);
                if (offset != null) {
                    dueDate = OffsetDateTime.now().plus(offset);
                }
            }
        }

        // Apply tolerance-days for overdue calculation
        Integer toleranceDays = planDefinitionParser.extractToleranceDays(match.action());
        if (dueDate != null && toleranceDays != null && toleranceDays > 0) {
            overdueDate = dueDate.plusDays(toleranceDays);
        }

        // Find or create the step instance for this action
        StepInstance step = stepInstanceService.createStep(
                protocolInstance, match.actionId(), dueDate, overdueDate, missedDate);

        // Complete the step
        StepInstance completedStep = stepInstanceService.completeStep(
                step.getId(), eventLog.getId(), event.getSource());

        // Update event log with match details
        eventLogService.updateMatchResult(
                eventLog.getId(),
                protocolInstance.getId(),
                match.planDefinition().getId(),
                match.actionId(),
                completedStep.getId(),
                ProcessingStatus.MATCHED);

        // Progressive step instantiation — create downstream pending steps
        progressiveStepInstantiation(match, protocolInstance);

        // Evaluate intelligence rules — nested sub-actions with conditions
        evaluateIntelligenceRules(match, event, protocolInstance, completedStep);

        // Audit
        auditService.auditSystem("COMPLIANCE", "STEP_COMPLETED",
                "StepInstance", completedStep.getId().toString(),
                Map.of(
                        "protocolInstanceId", protocolInstance.getId().toString(),
                        "actionId", match.actionId(),
                        "completionStatus", completedStep.getCompletionStatus() != null
                                ? completedStep.getCompletionStatus().getValue() : "UNKNOWN",
                        "eventId", event.getId()
                ));

        log.info("Match processed: eventId={}, protocolInstanceId={}, stepId={}, actionId={}",
                event.getId(), protocolInstance.getId(), completedStep.getId(), match.actionId());
    }

    /**
     * Processes an explicit match when the inbound CloudEvent carries an actionId.
     * Bypasses structural matching and directly targets the specified action.
     *
     * @return true if explicit match was handled, false if fallback to structural matching
     */
    private boolean processExplicitMatch(CloudEventMessage event, EventLog eventLog) {
        String actionId = event.getActionId();
        String protocolDefId = event.getProtocolDefinitionId();
        String patientId = event.getSubject();

        if (protocolDefId != null && !protocolDefId.isBlank()) {
            try {
                UUID pdUuid = UUID.fromString(protocolDefId);
                Optional<PlanDefinitionEntity> pdOpt = protocolDefinitionService.findById(pdUuid);
                if (pdOpt.isPresent()) {
                    PlanDefinitionEntity pdEntity = pdOpt.get();
                    String pdJson = objectMapper.writeValueAsString(pdEntity.getDefinition());
                    PlanDefinition pd = planDefinitionParser.parse(pdJson);

                    Optional<PlanDefinition.PlanDefinitionActionComponent> actionOpt =
                            planDefinitionParser.extractAllActions(pd).stream()
                                    .filter(a -> actionId.equals(a.getId()))
                                    .findFirst();

                    if (actionOpt.isPresent()) {
                        MatchResult match = new MatchResult(pdEntity, actionId, actionOpt.get());
                        processMatch(event, eventLog, match, patientId);
                        eventsMatchedCounter.increment();
                        log.info("Explicit match processed: eventId={}, actionId={}", event.getId(), actionId);
                        return true;
                    }
                }
            } catch (Exception ex) {
                log.warn("Explicit match failed for protocolDefId={}, actionId={}: {}",
                        protocolDefId, actionId, ex.getMessage());
            }
        }
        return false;
    }

    /**
     * Progressive step instantiation: after completing an action, find and create
     * downstream steps that depend on this action (via relatedAction).
     * These are created in PENDING state with computed due dates.
     */
    private void progressiveStepInstantiation(MatchResult match, ProtocolInstance protocolInstance) {
        try {
            String pdJson = objectMapper.writeValueAsString(match.planDefinition().getDefinition());
            PlanDefinition pd = planDefinitionParser.parse(pdJson);
            List<PlanDefinition.PlanDefinitionActionComponent> allActions =
                    planDefinitionParser.extractAllActions(pd);

            List<PlanDefinition.PlanDefinitionActionComponent> dependents =
                    planDefinitionParser.findDependentActions(allActions, match.actionId());

            for (PlanDefinition.PlanDefinitionActionComponent dependent : dependents) {
                if (dependent.getId() == null) continue;

                // Skip intelligence rules — they are not regular steps
                if (planDefinitionParser.isIntelligenceRule(dependent)) continue;

                // Compute due date from relatedAction offset
                OffsetDateTime dueDate = null;
                OffsetDateTime overdueDate = null;
                java.time.Duration offset = planDefinitionParser.computeRelatedActionOffset(
                        dependent, match.actionId());
                if (offset != null) {
                    dueDate = OffsetDateTime.now().plus(offset);
                    Integer toleranceDays = planDefinitionParser.extractToleranceDays(dependent);
                    if (toleranceDays != null && toleranceDays > 0) {
                        overdueDate = dueDate.plusDays(toleranceDays);
                    }
                }

                // Create the downstream step in PENDING state (if it has a due date)
                stepInstanceService.createStep(protocolInstance, dependent.getId(),
                        dueDate, overdueDate, null);
                log.debug("Progressive instantiation: created pending step for action {} in protocol {}",
                        dependent.getId(), protocolInstance.getId());
            }
        } catch (Exception ex) {
            log.warn("Progressive step instantiation failed for action {}: {}",
                    match.actionId(), ex.getMessage());
        }
    }

    /**
     * Evaluates intelligence rules — nested sub-actions that have conditions
     * but no triggers. If a sub-action's condition is met, logs an intelligence event.
     */
    private void evaluateIntelligenceRules(MatchResult match, CloudEventMessage event,
                                            ProtocolInstance protocolInstance,
                                            StepInstance completedStep) {
        try {
            PlanDefinition.PlanDefinitionActionComponent action = match.action();
            if (!action.hasAction()) return;

            Map<String, Object> variables = expressionEvaluationService.buildVariables(
                    event.getData(),
                    Map.of("id", event.getSubject() != null ? event.getSubject() : ""),
                    Map.of("actionId", match.actionId(),
                            "state", completedStep.getState().name()),
                    Map.of("instanceId", protocolInstance.getId().toString()));

            for (PlanDefinition.PlanDefinitionActionComponent subAction : action.getAction()) {
                if (planDefinitionParser.isIntelligenceRule(subAction)) {
                    String expression = planDefinitionParser.extractConditionExpression(subAction);
                    String language = planDefinitionParser.extractConditionLanguage(subAction);

                    if (expression != null && !expression.isBlank()) {
                        boolean result = expressionEvaluationService.evaluate(language, expression, variables);
                        if (result) {
                            String severity = planDefinitionParser.extractIntelligenceSeverity(subAction);
                            String target = planDefinitionParser.extractIntelligenceTarget(subAction);
                            String ruleId = subAction.getId() != null ? subAction.getId() : "unknown";

                            log.info("Intelligence rule triggered: ruleId={}, severity={}, target={}, "
                                            + "actionId={}, protocolInstanceId={}",
                                    ruleId, severity, target, match.actionId(), protocolInstance.getId());

                            auditService.auditSystem("COMPLIANCE", "INTELLIGENCE_RULE_TRIGGERED",
                                    "StepInstance", completedStep.getId().toString(),
                                    Map.of(
                                            "ruleId", ruleId,
                                            "severity", severity != null ? severity : "info",
                                            "target", target != null ? target : "provider",
                                            "actionId", match.actionId(),
                                            "eventId", event.getId()
                                    ));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Intelligence rule evaluation failed for action {}: {}",
                    match.actionId(), ex.getMessage());
        }
    }

    /**
     * Converts timing offset value/unit to a Java Duration.
     */
    private java.time.Duration convertTimingOffset(Object value, String unit) {
        try {
            long amount;
            if (value instanceof Number n) {
                amount = n.longValue();
            } else if (value instanceof java.math.BigDecimal bd) {
                amount = bd.longValue();
            } else {
                amount = Long.parseLong(value.toString());
            }
            return switch (unit.toLowerCase()) {
                case "s", "sec", "second", "seconds" -> java.time.Duration.ofSeconds(amount);
                case "min", "minute", "minutes" -> java.time.Duration.ofMinutes(amount);
                case "h", "hour", "hours" -> java.time.Duration.ofHours(amount);
                case "d", "day", "days" -> java.time.Duration.ofDays(amount);
                case "wk", "week", "weeks" -> java.time.Duration.ofDays(amount * 7);
                case "mo", "month", "months" -> java.time.Duration.ofDays(amount * 30);
                default -> java.time.Duration.ofDays(amount);
            };
        } catch (Exception ex) {
            log.warn("Cannot convert timing offset: value={}, unit={}", value, unit);
            return null;
        }
    }

    /**
     * Extracts the FHIR resource type from the CloudEvent.
     * Looks at the event type (e.g., "org.openhie.cr.encounter") or data.resourceType.
     */
    private String extractResourceType(CloudEventMessage event) {
        // Try from event data
        if (event.getData() != null && event.getData().containsKey("resourceType")) {
            return (String) event.getData().get("resourceType");
        }
        // Try inferring from event type
        String type = event.getType();
        if (type != null) {
            String[] parts = type.split("\\.");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                // Capitalize first letter
                return lastPart.substring(0, 1).toUpperCase() + lastPart.substring(1);
            }
        }
        return null;
    }

    /**
     * Extracts all (system, code) pairs from the CloudEvent data across multiple
     * FHIR paths used by protocol trigger code filters.
     * <p>
     * Reads from: {@code code}, {@code type}, {@code category}, {@code clinicalStatus}
     * (as CodeableConcepts) and {@code status} (as a simple FHIR code).
     */
    private List<CodePair> extractAllCodes(CloudEventMessage event) {
        if (event.getData() == null) return Collections.emptyList();
        List<CodePair> codes = new ArrayList<>();
        Map<String, Object> data = event.getData();

        // CodeableConcept fields: code, clinicalStatus (single object with coding[])
        extractCodesFromCodeableConcept(data.get("code"), codes);
        extractCodesFromCodeableConcept(data.get("clinicalStatus"), codes);

        // List<CodeableConcept> fields: type, category (array of objects with coding[])
        extractCodesFromCodeableConceptList(data.get("type"), codes);
        extractCodesFromCodeableConceptList(data.get("category"), codes);

        // Simple FHIR code field: status (plain string, no system)
        Object status = data.get("status");
        if (status instanceof String statusStr && !statusStr.isBlank()) {
            codes.add(new CodePair("", statusStr));
        }

        return codes;
    }

    /**
     * Extracts (system, code) pairs from a CodeableConcept (a Map with "coding" array).
     */
    private void extractCodesFromCodeableConcept(Object codeableConcept, List<CodePair> codes) {
        if (codeableConcept instanceof Map<?, ?> ccMap) {
            Object coding = ccMap.get("coding");
            if (coding instanceof List<?> codingList) {
                for (Object item : codingList) {
                    if (item instanceof Map<?, ?> codingMap) {
                        Object systemObj = codingMap.get("system");
                        String system = systemObj instanceof String s ? s : "";
                        Object codeObj = codingMap.get("code");
                        String code = codeObj instanceof String s ? s : null;
                        if (code != null && !code.isBlank()) {
                            codes.add(new CodePair(system, code));
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracts (system, code) pairs from a List of CodeableConcepts.
     */
    private void extractCodesFromCodeableConceptList(Object codeableConceptList, List<CodePair> codes) {
        if (codeableConceptList instanceof List<?> list) {
            for (Object item : list) {
                extractCodesFromCodeableConcept(item, codes);
            }
        }
    }

    /**
     * A code system + value pair extracted from an inbound event.
     */
    private record CodePair(String system, String code) {
        @Override
        public String toString() {
            return system.isEmpty() ? code : system + "|" + code;
        }
    }

    /**
     * Internal record for a confirmed match result.
     */
    private record MatchResult(PlanDefinitionEntity planDefinition, String actionId,
                                PlanDefinition.PlanDefinitionActionComponent action) {
    }
}
