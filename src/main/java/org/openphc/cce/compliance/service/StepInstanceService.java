package org.openphc.cce.compliance.service;

import org.openphc.cce.compliance.domain.entity.StepInstance;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.domain.enums.CompletionStatus;
import org.openphc.cce.compliance.domain.enums.StepState;
import org.openphc.cce.compliance.domain.repository.StepInstanceRepository;
import org.openphc.cce.compliance.kafka.model.SchedulerTriggerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages step instance lifecycle — creation, state transitions,
 * matching, and scheduler-driven transitions.
 */
@Service
@Transactional
public class StepInstanceService {

    private static final Logger log = LoggerFactory.getLogger(StepInstanceService.class);

    private final StepInstanceRepository stepInstanceRepository;

    public StepInstanceService(StepInstanceRepository stepInstanceRepository) {
        this.stepInstanceRepository = stepInstanceRepository;
    }

    /**
     * Creates a new step instance for a protocol instance action.
     *
     * @param protocolInstance the parent protocol instance
     * @param actionId         the PlanDefinition action ID
     * @param dueDate          the step due date (may be null if not time-bound)
     * @param overdueDate      the overdue threshold date
     * @param missedDate       the missed threshold date
     * @return the created step instance
     */
    public StepInstance createStep(ProtocolInstance protocolInstance, String actionId,
                                   OffsetDateTime dueDate, OffsetDateTime overdueDate,
                                   OffsetDateTime missedDate) {
        // Check for existing active step for this action
        List<StepInstance> existingList = stepInstanceRepository
                .findActiveByProtocolInstanceAndAction(protocolInstance.getId(), actionId);
        if (!existingList.isEmpty()) {
            log.debug("Active step already exists for action {} in protocol instance {}",
                    actionId, protocolInstance.getId());
            return existingList.get(0);
        }

        // Determine repeat index
        List<StepInstance> existingSteps = stepInstanceRepository
                .findByProtocolInstanceId(protocolInstance.getId());
        int repeatIndex = (int) existingSteps.stream()
                .filter(s -> actionId.equals(s.getActionId()))
                .count();

        StepInstance step = new StepInstance();
        step.setProtocolInstance(protocolInstance);
        step.setActionId(actionId);
        step.setRepeatIndex(repeatIndex);
        step.setState(dueDate != null ? StepState.PENDING : StepState.DUE);
        step.setDueDate(dueDate);
        step.setOverdueDate(overdueDate);
        step.setMissedDate(missedDate);
        step.setCreatedAt(OffsetDateTime.now());
        step.setUpdatedAt(OffsetDateTime.now());

        step = stepInstanceRepository.save(step);
        log.info("Created step instance: id={}, actionId={}, state={}, protocolInstanceId={}",
                step.getId(), actionId, step.getState(), protocolInstance.getId());
        return step;
    }

    /**
     * Completes a step instance with matching event information.
     *
     * @param stepInstanceId  the step instance ID
     * @param eventLogId      the event log entry that fulfilled this step
     * @param source          the source of completion
     * @return the updated step instance
     */
    public StepInstance completeStep(UUID stepInstanceId, UUID eventLogId, String source) {
        StepInstance step = stepInstanceRepository.findById(stepInstanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Step instance not found: " + stepInstanceId));

        OffsetDateTime now = OffsetDateTime.now();
        step.setState(StepState.COMPLETED);
        step.setCompletedAt(now);
        step.setCompletedBySource(source);
        step.setMatchedEventId(eventLogId);

        // Determine completion status
        if (step.getDueDate() != null) {
            if (now.isBefore(step.getDueDate())) {
                step.setCompletionStatus(CompletionStatus.EARLY);
            } else if (step.getOverdueDate() != null && now.isAfter(step.getOverdueDate())) {
                step.setCompletionStatus(CompletionStatus.LATE);
            } else {
                step.setCompletionStatus(CompletionStatus.ON_TIME);
            }
        } else {
            step.setCompletionStatus(CompletionStatus.ON_TIME);
        }

        step.setUpdatedAt(now);
        step = stepInstanceRepository.save(step);
        log.info("Completed step: id={}, completionStatus={}", step.getId(), step.getCompletionStatus());
        return step;
    }

    /**
     * Applies a scheduler-driven state transition to a step instance.
     * Transitions: PENDING → DUE, DUE → OVERDUE, OVERDUE → MISSED.
     *
     * @param trigger the scheduler trigger message
     */
    public void applySchedulerTransition(SchedulerTriggerMessage trigger) {
        StepInstance step = stepInstanceRepository.findById(trigger.getStepInstanceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Step instance not found: " + trigger.getStepInstanceId()));

        StepState currentState = step.getState();
        String transitionType = trigger.getTransitionType();
        OffsetDateTime now = OffsetDateTime.now();

        StepState newState = switch (transitionType) {
            case "DUE" -> {
                if (currentState != StepState.PENDING) {
                    log.warn("Cannot transition to DUE from state={}, stepId={}",
                            currentState, step.getId());
                    yield currentState;
                }
                yield StepState.DUE;
            }
            case "OVERDUE" -> {
                if (currentState != StepState.DUE) {
                    log.warn("Cannot transition to OVERDUE from state={}, stepId={}",
                            currentState, step.getId());
                    yield currentState;
                }
                yield StepState.OVERDUE;
            }
            case "MISSED" -> {
                if (currentState != StepState.OVERDUE) {
                    log.warn("Cannot transition to MISSED from state={}, stepId={}",
                            currentState, step.getId());
                    yield currentState;
                }
                yield StepState.MISSED;
            }
            default -> {
                log.error("Unknown transition type: {}, stepId={}", transitionType, step.getId());
                yield currentState;
            }
        };

        if (newState != currentState) {
            step.setState(newState);
            step.setUpdatedAt(now);
            stepInstanceRepository.save(step);
            log.info("Step {} transitioned from {} to {} (trigger={})",
                    step.getId(), currentState, newState, transitionType);
        }
    }

    /**
     * Skips a step instance.
     */
    public StepInstance skipStep(UUID stepInstanceId) {
        StepInstance step = stepInstanceRepository.findById(stepInstanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Step instance not found: " + stepInstanceId));
        step.setState(StepState.SKIPPED);
        step.setUpdatedAt(OffsetDateTime.now());
        return stepInstanceRepository.save(step);
    }

    @Transactional(readOnly = true)
    public List<StepInstance> findByProtocolInstanceId(UUID protocolInstanceId) {
        return stepInstanceRepository.findByProtocolInstanceId(protocolInstanceId);
    }

    /**
     * Finds active (PENDING, DUE, OVERDUE) step instances for a given protocol instance and action.
     *
     * @param protocolInstanceId the protocol instance UUID
     * @param actionId           the PlanDefinition action ID
     * @return list of step instances in eligible states
     */
    @Transactional(readOnly = true)
    public List<StepInstance> findActiveByProtocolInstanceAndAction(UUID protocolInstanceId, String actionId) {
        return stepInstanceRepository.findActiveByProtocolInstanceAndAction(protocolInstanceId, actionId);
    }

    @Transactional(readOnly = true)
    public List<StepInstance> findByProtocolInstanceIdAndState(UUID protocolInstanceId, StepState state) {
        return stepInstanceRepository.findByProtocolInstanceIdAndState(protocolInstanceId, state);
    }

    @Transactional(readOnly = true)
    public Optional<StepInstance> findById(UUID id) {
        return stepInstanceRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<StepInstance> findStepsDueBy(OffsetDateTime cutoff) {
        return stepInstanceRepository.findStepsDueBy(cutoff);
    }

    @Transactional(readOnly = true)
    public List<StepInstance> findStepsOverdueBy(OffsetDateTime cutoff) {
        return stepInstanceRepository.findStepsOverdueBy(cutoff);
    }
}
