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
            // Step 3: Extract resource type and code from event
            String resourceType = extractResourceType(event);
            String codeSystem = extractCodeSystem(event);
            String codeValue = extractCodeValue(event);
            String patientId = event.getSubject();

            if (resourceType == null) {
                log.warn("Cannot determine resource type from event: id={}", event.getId());
                return;
            }

            // Step 4: Tier 1 — Structural matching
            List<TriggerIndex> structuralMatches = triggerMatchingService
                    .findStructuralMatches(resourceType, codeSystem, codeValue);

            if (structuralMatches.isEmpty()) {
                log.info("No structural matches for event: id={}, resourceType={}", event.getId(), resourceType);
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

                        // Evaluate Tier 2 condition
                        if (triggerMatchingService.evaluateCondition(action, variables)) {
                            confirmedMatches.add(new MatchResult(pdEntity, triggerMatch.getActionId(), action));
                        }
                    }
                } catch (Exception ex) {
                    log.error("Error evaluating condition for PD {} action {}: {}",
                            pdEntity.getId(), triggerMatch.getActionId(), ex.getMessage(), ex);
                }
            }

            // Step 6: Process matches
            if (confirmedMatches.isEmpty()) {
                eventsZeroMatchCounter.increment();
                log.info("No confirmed matches after Tier 2 evaluation for event: id={}", event.getId());

            } else if (confirmedMatches.size() == 1) {
                // Single match — process normally
                MatchResult match = confirmedMatches.get(0);
                processMatch(event, eventLog, match, patientId);
                eventsMatchedCounter.increment();

            } else {
                // Ambiguous — multiple matches
                eventsAmbiguousCounter.increment();
                eventLogService.updateMatchResult(eventLog.getId(), null, null, null, null,
                        ProcessingStatus.AMBIGUOUS);

                // Record deviations for ambiguous matches
                for (MatchResult match : confirmedMatches) {
                    ProtocolInstance pi = protocolInstanceService.enrollOrGetActive(patientId, match.planDefinition());
                    List<StepInstance> activeSteps = stepInstanceService
                            .findByProtocolInstanceIdAndState(pi.getId(), StepState.DUE);
                    if (!activeSteps.isEmpty()) {
                        deviationService.recordDeviation(pi, activeSteps.get(0), DeviationType.AMBIGUOUS,
                                Map.of("matchCount", confirmedMatches.size(),
                                        "eventId", event.getId()));
                    }
                }
                log.warn("Ambiguous match: event id={} matched {} actions", event.getId(), confirmedMatches.size());
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
     * Extracts the code system from the CloudEvent data.
     */
    private String extractCodeSystem(CloudEventMessage event) {
        if (event.getData() == null) return null;
        Object code = event.getData().get("code");
        if (code instanceof Map<?, ?> codeMap) {
            Object coding = codeMap.get("coding");
            if (coding instanceof List<?> codingList && !codingList.isEmpty()) {
                Object first = codingList.get(0);
                if (first instanceof Map<?, ?> codingMap) {
                    return (String) codingMap.get("system");
                }
            }
        }
        return null;
    }

    /**
     * Extracts the code value from the CloudEvent data.
     */
    private String extractCodeValue(CloudEventMessage event) {
        if (event.getData() == null) return null;
        Object code = event.getData().get("code");
        if (code instanceof Map<?, ?> codeMap) {
            Object coding = codeMap.get("coding");
            if (coding instanceof List<?> codingList && !codingList.isEmpty()) {
                Object first = codingList.get(0);
                if (first instanceof Map<?, ?> codingMap) {
                    return (String) codingMap.get("code");
                }
            }
        }
        return null;
    }

    /**
     * Internal record for a confirmed match result.
     */
    private record MatchResult(PlanDefinitionEntity planDefinition, String actionId,
                                PlanDefinition.PlanDefinitionActionComponent action) {
    }
}
