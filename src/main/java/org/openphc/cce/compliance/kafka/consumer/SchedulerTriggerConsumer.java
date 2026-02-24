package org.openphc.cce.compliance.kafka.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.openphc.cce.compliance.kafka.model.SchedulerTriggerMessage;
import org.openphc.cce.compliance.service.StepInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for scheduler trigger messages from {@code cce.scheduler.triggers}.
 * <p>
 * The Scheduler Service fires time-based triggers (DUE → OVERDUE, OVERDUE → MISSED)
 * and this consumer applies the state transitions to the relevant step instances.
 */
@Component
public class SchedulerTriggerConsumer {

    private static final Logger log = LoggerFactory.getLogger(SchedulerTriggerConsumer.class);

    private final StepInstanceService stepInstanceService;
    private final Counter triggerReceivedCounter;
    private final Counter triggerErrorCounter;

    public SchedulerTriggerConsumer(StepInstanceService stepInstanceService, MeterRegistry meterRegistry) {
        this.stepInstanceService = stepInstanceService;
        this.triggerReceivedCounter = Counter.builder("cce.scheduler.triggers.received")
                .description("Scheduler trigger events received")
                .register(meterRegistry);
        this.triggerErrorCounter = Counter.builder("cce.scheduler.triggers.errors")
                .description("Scheduler trigger events that failed processing")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${cce.kafka.topics.scheduler-triggers}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory",
            properties = {
                "spring.json.value.default.type=org.openphc.cce.compliance.kafka.model.SchedulerTriggerMessage"
            }
    )
    public void onSchedulerTrigger(@Payload SchedulerTriggerMessage trigger, Acknowledgment ack) {
        String correlationId = trigger.getCorrelationId();
        MDC.put("correlationId", correlationId != null ? correlationId : "scheduler-trigger");
        try {
            log.info("Received scheduler trigger: stepInstanceId={}, transitionType={}",
                    trigger.getStepInstanceId(), trigger.getTransitionType());
            triggerReceivedCounter.increment();

            stepInstanceService.applySchedulerTransition(trigger);

            ack.acknowledge();
            log.info("Successfully processed scheduler trigger for stepInstanceId={}",
                    trigger.getStepInstanceId());
        } catch (Exception ex) {
            triggerErrorCounter.increment();
            log.error("Error processing scheduler trigger: stepInstanceId={}",
                    trigger.getStepInstanceId(), ex);
            throw ex;
        } finally {
            MDC.remove("correlationId");
        }
    }
}
