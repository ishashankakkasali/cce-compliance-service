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
import org.openphc.cce.compliance.kafka.model.CloudEventMessage;
import org.openphc.cce.compliance.service.ComplianceEngine;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InboundEventConsumer Tests")
class InboundEventConsumerTest {

    @Mock private ComplianceEngine complianceEngine;
    @Mock private Acknowledgment acknowledgment;

    private MeterRegistry meterRegistry;
    private InboundEventConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new InboundEventConsumer(complianceEngine, meterRegistry);
    }

    private CloudEventMessage createCloudEvent() {
        CloudEventMessage event = new CloudEventMessage();
        event.setId(UUID.randomUUID().toString());
        event.setType("org.openphc.fhir.Encounter.create");
        event.setSource("ehr-system");
        event.setSubject("Patient/123");
        event.setCorrelationId(UUID.randomUUID().toString());
        event.setTime(OffsetDateTime.now());
        event.setData(Map.of("resourceType", "Encounter"));
        return event;
    }

    @Nested
    @DisplayName("Successful event processing")
    class SuccessfulProcessing {

        @Test
        @DisplayName("should process event and acknowledge")
        void shouldProcessAndAcknowledge() {
            CloudEventMessage event = createCloudEvent();

            consumer.onInboundEvent(event, acknowledgment);

            verify(complianceEngine).processInboundEvent(event);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should increment received counter")
        void shouldIncrementReceivedCounter() {
            CloudEventMessage event = createCloudEvent();

            consumer.onInboundEvent(event, acknowledgment);

            Counter counter = meterRegistry.find("cce.events.received").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Failed event processing")
    class FailedProcessing {

        @Test
        @DisplayName("should not acknowledge on error and rethrow exception")
        void shouldNotAcknowledgeOnError() {
            CloudEventMessage event = createCloudEvent();
            doThrow(new RuntimeException("Processing failed"))
                    .when(complianceEngine).processInboundEvent(event);

            assertThatThrownBy(() -> consumer.onInboundEvent(event, acknowledgment))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Processing failed");

            verify(acknowledgment, never()).acknowledge();
        }

        @Test
        @DisplayName("should increment error counter on failure")
        void shouldIncrementErrorCounter() {
            CloudEventMessage event = createCloudEvent();
            doThrow(new RuntimeException("fail"))
                    .when(complianceEngine).processInboundEvent(event);

            try {
                consumer.onInboundEvent(event, acknowledgment);
            } catch (RuntimeException ignored) {
            }

            Counter errorCounter = meterRegistry.find("cce.events.errors").counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }
    }
}
