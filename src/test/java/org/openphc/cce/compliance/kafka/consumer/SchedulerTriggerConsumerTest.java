package org.openphc.cce.compliance.kafka.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.kafka.model.SchedulerTriggerMessage;
import org.openphc.cce.compliance.service.StepInstanceService;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulerTriggerConsumer Tests")
class SchedulerTriggerConsumerTest {

    @Mock private StepInstanceService stepInstanceService;
    @Mock private Acknowledgment acknowledgment;

    private MeterRegistry meterRegistry;
    private SchedulerTriggerConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new SchedulerTriggerConsumer(stepInstanceService, meterRegistry);
    }

    private SchedulerTriggerMessage createTrigger(String transitionType) {
        SchedulerTriggerMessage trigger = new SchedulerTriggerMessage();
        trigger.setStepInstanceId(UUID.randomUUID());
        trigger.setTransitionType(transitionType);
        trigger.setCorrelationId(UUID.randomUUID().toString());
        return trigger;
    }

    @Nested
    @DisplayName("Successful trigger processing")
    class SuccessfulProcessing {

        @Test
        @DisplayName("should process DUE_TO_OVERDUE trigger and acknowledge")
        void shouldProcessDueToOverdue() {
            SchedulerTriggerMessage trigger = createTrigger("DUE_TO_OVERDUE");

            consumer.onSchedulerTrigger(trigger, acknowledgment);

            verify(stepInstanceService).applySchedulerTransition(trigger);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should process OVERDUE_TO_MISSED trigger and acknowledge")
        void shouldProcessOverdueToMissed() {
            SchedulerTriggerMessage trigger = createTrigger("OVERDUE_TO_MISSED");

            consumer.onSchedulerTrigger(trigger, acknowledgment);

            verify(stepInstanceService).applySchedulerTransition(trigger);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should increment received counter")
        void shouldIncrementReceivedCounter() {
            SchedulerTriggerMessage trigger = createTrigger("DUE_TO_OVERDUE");

            consumer.onSchedulerTrigger(trigger, acknowledgment);

            Counter counter = meterRegistry.find("cce.scheduler.triggers.received").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Failed trigger processing")
    class FailedProcessing {

        @Test
        @DisplayName("should not acknowledge on error and rethrow")
        void shouldNotAcknowledgeOnError() {
            SchedulerTriggerMessage trigger = createTrigger("DUE_TO_OVERDUE");
            doThrow(new RuntimeException("Step not found"))
                    .when(stepInstanceService).applySchedulerTransition(trigger);

            assertThatThrownBy(() -> consumer.onSchedulerTrigger(trigger, acknowledgment))
                    .isInstanceOf(RuntimeException.class);

            verify(acknowledgment, never()).acknowledge();
        }

        @Test
        @DisplayName("should increment error counter on failure")
        void shouldIncrementErrorCounter() {
            SchedulerTriggerMessage trigger = createTrigger("DUE_TO_OVERDUE");
            doThrow(new RuntimeException("fail"))
                    .when(stepInstanceService).applySchedulerTransition(trigger);

            try {
                consumer.onSchedulerTrigger(trigger, acknowledgment);
            } catch (RuntimeException ignored) {
            }

            Counter errorCounter = meterRegistry.find("cce.scheduler.triggers.errors").counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Null correlation ID")
    class NullCorrelationId {

        @Test
        @DisplayName("should handle null correlation ID gracefully")
        void shouldHandleNullCorrelationId() {
            SchedulerTriggerMessage trigger = createTrigger("DUE_TO_OVERDUE");
            trigger.setCorrelationId(null);

            consumer.onSchedulerTrigger(trigger, acknowledgment);

            verify(stepInstanceService).applySchedulerTransition(trigger);
            verify(acknowledgment).acknowledge();
        }
    }
}
