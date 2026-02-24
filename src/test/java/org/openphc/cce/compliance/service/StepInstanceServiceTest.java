package org.openphc.cce.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.domain.entity.StepInstance;
import org.openphc.cce.compliance.domain.enums.CompletionStatus;
import org.openphc.cce.compliance.domain.enums.StepState;
import org.openphc.cce.compliance.domain.repository.StepInstanceRepository;
import org.openphc.cce.compliance.kafka.model.SchedulerTriggerMessage;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StepInstanceService Tests")
class StepInstanceServiceTest {

    @Mock private StepInstanceRepository stepInstanceRepository;

    private StepInstanceService service;

    @BeforeEach
    void setUp() {
        service = new StepInstanceService(stepInstanceRepository);
    }

    private ProtocolInstance createProtocolInstance() {
        ProtocolInstance pi = new ProtocolInstance();
        pi.setId(UUID.randomUUID());
        pi.setPatientId("Patient/123");
        return pi;
    }

    @Nested
    @DisplayName("createStep")
    class CreateStep {

        @Test
        @DisplayName("should create new step with DUE state when no due date")
        void shouldCreateNewStepDueState() {
            ProtocolInstance pi = createProtocolInstance();

            when(stepInstanceRepository.findActiveByProtocolInstanceAndAction(pi.getId(), "action-1"))
                    .thenReturn(Collections.emptyList());
            when(stepInstanceRepository.findByProtocolInstanceId(pi.getId()))
                    .thenReturn(Collections.emptyList());
            when(stepInstanceRepository.save(any(StepInstance.class)))
                    .thenAnswer(inv -> {
                        StepInstance si = inv.getArgument(0);
                        si.setId(UUID.randomUUID());
                        return si;
                    });

            StepInstance result = service.createStep(pi, "action-1", null, null, null);

            assertThat(result.getActionId()).isEqualTo("action-1");
            assertThat(result.getState()).isEqualTo(StepState.DUE);
            assertThat(result.getRepeatIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("should create step with PENDING state when due date provided")
        void shouldCreateStepPendingState() {
            ProtocolInstance pi = createProtocolInstance();
            OffsetDateTime dueDate = OffsetDateTime.now().plusDays(7);

            when(stepInstanceRepository.findActiveByProtocolInstanceAndAction(pi.getId(), "action-1"))
                    .thenReturn(Collections.emptyList());
            when(stepInstanceRepository.findByProtocolInstanceId(pi.getId()))
                    .thenReturn(Collections.emptyList());
            when(stepInstanceRepository.save(any(StepInstance.class)))
                    .thenAnswer(inv -> {
                        StepInstance si = inv.getArgument(0);
                        si.setId(UUID.randomUUID());
                        return si;
                    });

            StepInstance result = service.createStep(pi, "action-1", dueDate, null, null);

            assertThat(result.getState()).isEqualTo(StepState.PENDING);
            assertThat(result.getDueDate()).isEqualTo(dueDate);
        }

        @Test
        @DisplayName("should return existing active step")
        void shouldReturnExistingActiveStep() {
            ProtocolInstance pi = createProtocolInstance();
            StepInstance existingStep = new StepInstance();
            existingStep.setId(UUID.randomUUID());
            existingStep.setActionId("action-1");

            when(stepInstanceRepository.findActiveByProtocolInstanceAndAction(pi.getId(), "action-1"))
                    .thenReturn(List.of(existingStep));

            StepInstance result = service.createStep(pi, "action-1", null, null, null);

            assertThat(result.getId()).isEqualTo(existingStep.getId());
            verify(stepInstanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("should calculate correct repeat index")
        void shouldCalculateRepeatIndex() {
            ProtocolInstance pi = createProtocolInstance();

            StepInstance existing1 = new StepInstance();
            existing1.setActionId("action-1");
            StepInstance existing2 = new StepInstance();
            existing2.setActionId("action-1");
            StepInstance existing3 = new StepInstance();
            existing3.setActionId("action-2");

            when(stepInstanceRepository.findActiveByProtocolInstanceAndAction(pi.getId(), "action-1"))
                    .thenReturn(Collections.emptyList());
            when(stepInstanceRepository.findByProtocolInstanceId(pi.getId()))
                    .thenReturn(List.of(existing1, existing2, existing3));
            when(stepInstanceRepository.save(any(StepInstance.class)))
                    .thenAnswer(inv -> {
                        StepInstance si = inv.getArgument(0);
                        si.setId(UUID.randomUUID());
                        return si;
                    });

            StepInstance result = service.createStep(pi, "action-1", null, null, null);

            assertThat(result.getRepeatIndex()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("completeStep")
    class CompleteStep {

        @Test
        @DisplayName("should complete step as ON_TIME when no due date")
        void shouldCompleteOnTimeNoDueDate() {
            UUID stepId = UUID.randomUUID();
            UUID eventLogId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(stepId);
            step.setState(StepState.DUE);

            when(stepInstanceRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StepInstance result = service.completeStep(stepId, eventLogId, "source-1");

            assertThat(result.getState()).isEqualTo(StepState.COMPLETED);
            assertThat(result.getCompletionStatus()).isEqualTo(CompletionStatus.ON_TIME);
            assertThat(result.getCompletedBySource()).isEqualTo("source-1");
            assertThat(result.getMatchedEventId()).isEqualTo(eventLogId);
        }

        @Test
        @DisplayName("should complete step as EARLY when before due date")
        void shouldCompleteEarly() {
            UUID stepId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(stepId);
            step.setState(StepState.DUE);
            step.setDueDate(OffsetDateTime.now().plusDays(7));

            when(stepInstanceRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StepInstance result = service.completeStep(stepId, UUID.randomUUID(), "source");

            assertThat(result.getCompletionStatus()).isEqualTo(CompletionStatus.EARLY);
        }

        @Test
        @DisplayName("should complete step as LATE when after overdue date")
        void shouldCompleteLate() {
            UUID stepId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(stepId);
            step.setState(StepState.OVERDUE);
            step.setDueDate(OffsetDateTime.now().minusDays(14));
            step.setOverdueDate(OffsetDateTime.now().minusDays(7));

            when(stepInstanceRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StepInstance result = service.completeStep(stepId, UUID.randomUUID(), "source");

            assertThat(result.getCompletionStatus()).isEqualTo(CompletionStatus.LATE);
        }

        @Test
        @DisplayName("should throw when step not found")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(stepInstanceRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.completeStep(id, UUID.randomUUID(), "source"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("applySchedulerTransition")
    class ApplySchedulerTransition {

        @Test
        @DisplayName("should transition PENDING -> DUE")
        void shouldTransitionPendingToDue() {
            UUID stepId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(stepId);
            step.setState(StepState.PENDING);

            SchedulerTriggerMessage trigger = new SchedulerTriggerMessage();
            trigger.setStepInstanceId(stepId);
            trigger.setTransitionType("DUE");

            when(stepInstanceRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.applySchedulerTransition(trigger);

            assertThat(step.getState()).isEqualTo(StepState.DUE);
        }

        @Test
        @DisplayName("should transition DUE -> OVERDUE")
        void shouldTransitionDueToOverdue() {
            UUID stepId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(stepId);
            step.setState(StepState.DUE);

            SchedulerTriggerMessage trigger = new SchedulerTriggerMessage();
            trigger.setStepInstanceId(stepId);
            trigger.setTransitionType("OVERDUE");

            when(stepInstanceRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.applySchedulerTransition(trigger);

            assertThat(step.getState()).isEqualTo(StepState.OVERDUE);
        }

        @Test
        @DisplayName("should transition OVERDUE -> MISSED")
        void shouldTransitionOverdueToMissed() {
            UUID stepId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(stepId);
            step.setState(StepState.OVERDUE);

            SchedulerTriggerMessage trigger = new SchedulerTriggerMessage();
            trigger.setStepInstanceId(stepId);
            trigger.setTransitionType("MISSED");

            when(stepInstanceRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.applySchedulerTransition(trigger);

            assertThat(step.getState()).isEqualTo(StepState.MISSED);
        }

        @Test
        @DisplayName("should not transition from invalid state")
        void shouldNotTransitionFromInvalidState() {
            UUID stepId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(stepId);
            step.setState(StepState.COMPLETED);

            SchedulerTriggerMessage trigger = new SchedulerTriggerMessage();
            trigger.setStepInstanceId(stepId);
            trigger.setTransitionType("DUE");

            when(stepInstanceRepository.findById(stepId)).thenReturn(Optional.of(step));

            service.applySchedulerTransition(trigger);

            assertThat(step.getState()).isEqualTo(StepState.COMPLETED);
            verify(stepInstanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("should handle unknown transition type")
        void shouldHandleUnknownTransitionType() {
            UUID stepId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(stepId);
            step.setState(StepState.DUE);

            SchedulerTriggerMessage trigger = new SchedulerTriggerMessage();
            trigger.setStepInstanceId(stepId);
            trigger.setTransitionType("UNKNOWN");

            when(stepInstanceRepository.findById(stepId)).thenReturn(Optional.of(step));

            service.applySchedulerTransition(trigger);

            assertThat(step.getState()).isEqualTo(StepState.DUE);
        }
    }

    @Nested
    @DisplayName("skipStep")
    class SkipStep {

        @Test
        @DisplayName("should skip a step")
        void shouldSkipStep() {
            UUID stepId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(stepId);
            step.setState(StepState.DUE);

            when(stepInstanceRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StepInstance result = service.skipStep(stepId);

            assertThat(result.getState()).isEqualTo(StepState.SKIPPED);
        }
    }
}
