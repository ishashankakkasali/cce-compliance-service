package org.openphc.cce.compliance.kafka.producer;

import org.openphc.cce.compliance.kafka.config.KafkaTopicProperties;
import org.openphc.cce.compliance.kafka.model.IntelligenceTriggerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Produces intelligence trigger events to {@code cce.intelligence.triggers} topic.
 * <p>
 * These events notify the Intelligence Service of deviations detected
 * by the Compliance Engine (e.g., OVERDUE, MISSED, AMBIGUOUS steps).
 */
@Component
public class IntelligenceTriggerProducer {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceTriggerProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    public IntelligenceTriggerProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                       KafkaTopicProperties topicProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicProperties = topicProperties;
    }

    /**
     * Publishes an intelligence trigger event.
     *
     * @param event the trigger event describing the deviation
     */
    public void publishTrigger(IntelligenceTriggerEvent event) {
        String topic = topicProperties.getIntelligenceTriggers();
        String key = event.getProtocolInstanceId() != null
                ? event.getProtocolInstanceId().toString()
                : event.getId().toString();

        log.info("Publishing intelligence trigger: id={}, type={}, subject={}, topic={}",
                event.getId(), event.getType(), event.getSubject(), topic);

        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish intelligence trigger: id={}", event.getId(), ex);
                    } else {
                        log.debug("Published intelligence trigger: id={}, offset={}",
                                event.getId(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
