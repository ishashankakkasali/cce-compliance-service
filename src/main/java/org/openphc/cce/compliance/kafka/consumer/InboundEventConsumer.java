package org.openphc.cce.compliance.kafka.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.openphc.cce.compliance.kafka.model.CloudEventMessage;
import org.openphc.cce.compliance.service.ComplianceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for inbound FHIR events from {@code cce.events.inbound}.
 * <p>
 * Each message is a CloudEvents v1.0 envelope containing a FHIR resource
 * (e.g., Encounter, Observation, Immunization). This consumer delegates
 * processing to the {@link ComplianceEngine}.
 */
@Component
public class InboundEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InboundEventConsumer.class);

    private final ComplianceEngine complianceEngine;
    private final Counter eventsReceivedCounter;
    private final Counter eventsErrorCounter;

    public InboundEventConsumer(ComplianceEngine complianceEngine, MeterRegistry meterRegistry) {
        this.complianceEngine = complianceEngine;
        this.eventsReceivedCounter = Counter.builder("cce.events.received")
                .tag("topic", "inbound")
                .description("Inbound events received from Kafka")
                .register(meterRegistry);
        this.eventsErrorCounter = Counter.builder("cce.events.errors")
                .tag("topic", "inbound")
                .description("Inbound events that failed processing")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${cce.kafka.topics.inbound-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInboundEvent(@Payload CloudEventMessage event, Acknowledgment ack) {
        String correlationId = event.getCorrelationId();
        MDC.put("correlationId", correlationId);
        try {
            log.info("Received inbound event: id={}, type={}, subject={}",
                    event.getId(), event.getType(), event.getSubject());
            eventsReceivedCounter.increment();

            complianceEngine.processInboundEvent(event);

            ack.acknowledge();
            log.info("Successfully processed inbound event: id={}", event.getId());
        } catch (Exception ex) {
            eventsErrorCounter.increment();
            log.error("Error processing inbound event: id={}", event.getId(), ex);
            // Do NOT acknowledge — allow redelivery or DLQ routing
            throw ex;
        } finally {
            MDC.remove("correlationId");
        }
    }
}
