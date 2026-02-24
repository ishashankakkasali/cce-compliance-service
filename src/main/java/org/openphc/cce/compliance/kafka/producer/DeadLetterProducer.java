package org.openphc.cce.compliance.kafka.producer;

import org.openphc.cce.compliance.domain.enums.FailureStage;
import org.openphc.cce.compliance.kafka.config.KafkaTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Produces dead-letter events to {@code cce.deadletter} topic.
 * <p>
 * Events that cannot be processed after exhausting retries are routed here
 * for manual inspection and resolution.
 */
@Component
public class DeadLetterProducer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    public DeadLetterProducer(KafkaTemplate<String, Object> kafkaTemplate,
                              KafkaTopicProperties topicProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicProperties = topicProperties;
    }

    /**
     * Publishes a dead-letter event when processing fails.
     *
     * @param originalPayload the original event payload
     * @param failureReason   human-readable error description
     * @param failureStage    the stage at which the failure occurred
     * @param correlationId   the correlation ID for tracing
     */
    public void publishDeadLetter(Object originalPayload, String failureReason,
                                  FailureStage failureStage, String correlationId) {
        String topic = topicProperties.getDeadLetter();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("payload", originalPayload);
        envelope.put("failureReason", failureReason);
        envelope.put("failureStage", failureStage.getValue());
        envelope.put("correlationId", correlationId);
        envelope.put("timestamp", OffsetDateTime.now().toString());

        log.warn("Publishing dead-letter event: reason={}, stage={}, correlationId={}",
                failureReason, failureStage.getValue(), correlationId);

        kafkaTemplate.send(topic, correlationId, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("CRITICAL: Failed to publish dead-letter event: correlationId={}",
                                correlationId, ex);
                    } else {
                        log.info("Published dead-letter event: correlationId={}, offset={}",
                                correlationId,
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
