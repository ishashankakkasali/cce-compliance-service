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
    private final DeadLetterService deadLetterService;
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
                             DeadLetterService deadLetterService,
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
        this.deadLetterService = deadLetterService;
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
            deadLetterService.recordDeadLetter(event, ex.getMessage(), FailureStage.PROCESSING);
            deadLetterProducer.publishDeadLetter(event, ex.getMessage(),
                    FailureStage.PROCESSING, event.getCorrelationId());
            throw ex;
        } finally {
            matchingSample.stop(matchingDurationTimer);
        }
    }

    /**
     * Processes a confirmed single match — enrolls patient, completes step, updates event log.
     */
    private void processMatch(CloudEventMessage event, EventLog eventLog,
                               MatchResult match, String patientId) {
        // Enroll patient (or get existing active instance)
        ProtocolInstance protocolInstance = protocolInstanceService
                .enrollOrGetActive(patientId, match.planDefinition());

        // Find or create the step instance for this action
        StepInstance step = stepInstanceService.createStep(
                protocolInstance, match.actionId(), null, null, null);

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
