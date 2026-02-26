package org.openphc.cce.compliance.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka topic name configuration for all CCE topics.
 * Bound from {@code cce.kafka.topics.*} application properties.
 */
@Configuration
@ConfigurationProperties(prefix = "cce.kafka.topics")
public class KafkaTopicProperties {

    private String inboundEvents = "cce.events.inbound";
    private String schedulerTriggers = "cce.scheduler.triggers";
    private String intelligenceTriggers = "cce.intelligence.triggers";
    private String deadLetter = "cce.deadletter";

    public String getInboundEvents() {
        return inboundEvents;
    }

    public void setInboundEvents(String inboundEvents) {
        this.inboundEvents = inboundEvents;
    }

    public String getSchedulerTriggers() {
        return schedulerTriggers;
    }

    public void setSchedulerTriggers(String schedulerTriggers) {
        this.schedulerTriggers = schedulerTriggers;
    }

    public String getIntelligenceTriggers() {
        return intelligenceTriggers;
    }

    public void setIntelligenceTriggers(String intelligenceTriggers) {
        this.intelligenceTriggers = intelligenceTriggers;
    }

    public String getDeadLetter() {
        return deadLetter;
    }

    public void setDeadLetter(String deadLetter) {
        this.deadLetter = deadLetter;
    }
}
