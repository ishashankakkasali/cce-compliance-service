package org.openphc.cce.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.domain.entity.Deviation;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.domain.entity.StepInstance;
import org.openphc.cce.compliance.domain.enums.DeviationType;
import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.compliance.domain.enums.StepState;
import org.openphc.cce.compliance.domain.repository.DeviationRepository;
import org.openphc.cce.compliance.kafka.model.IntelligenceTriggerEvent;
import org.openphc.cce.compliance.kafka.producer.IntelligenceTriggerProducer;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviationService Tests")
class DeviationServiceTest {

    @Mock private DeviationRepository deviationRepository;
    @Mock private IntelligenceTriggerProducer intelligenceTriggerProducer;

    private DeviationService service;

    @BeforeEach
    void setUp() {
        service = new DeviationService(deviationRepository, intelligenceTriggerProducer);
    }

    @Nested
    @DisplayName("recordDeviation")
    class RecordDeviation {

        @Test
        @DisplayName("should record deviation and publish intelligence trigger")
        void shouldRecordDeviationAndPublish() {
            ProtocolInstance pi = new ProtocolInstance();
            pi.setId(UUID.randomUUID());
            pi.setPatientId("Patient/123");
            pi.setProtocolCanonical("http://example.org/pd|1.0");
            pi.setStatus(ProtocolInstanceStatus.ACTIVE);

            StepInstance step = new StepInstance();
            step.setId(UUID.randomUUID());
            step.setActionId("action-1");
            step.setState(StepState.OVERDUE);

            Map<String, Object> metadata = Map.of("reason", "overdue");

            when(deviationRepository.save(any(Deviation.class)))
                    .thenAnswer(inv -> {
                        Deviation d = inv.getArgument(0);
                        d.setId(UUID.randomUUID());
                        return d;
                    });

            Deviation result = service.recordDeviation(pi, step, DeviationType.OVERDUE, metadata);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getDeviationType()).isEqualTo(DeviationType.OVERDUE);
            assertThat(result.getMetadata()).containsEntry("reason", "overdue");
            verify(deviationRepository).save(any(Deviation.class));

            // Verify intelligence trigger published
            ArgumentCaptor<IntelligenceTriggerEvent> captor = ArgumentCaptor.forClass(IntelligenceTriggerEvent.class);
            verify(intelligenceTriggerProducer).publishTrigger(captor.capture());

            IntelligenceTriggerEvent trigger = captor.getValue();
            assertThat(trigger.getSubject()).isEqualTo("Patient/123");
            assertThat(trigger.getProtocolInstanceId()).isEqualTo(pi.getId());
            assertThat(trigger.getStepInstanceId()).isEqualTo(step.getId());
            assertThat(trigger.getDeviationType()).isEqualTo("overdue");
            assertThat(trigger.getType()).contains("overdue");
        }

        @Test
        @DisplayName("should record AMBIGUOUS deviation")
        void shouldRecordAmbiguousDeviation() {
            ProtocolInstance pi = new ProtocolInstance();
            pi.setId(UUID.randomUUID());
            pi.setPatientId("Patient/456");
            pi.setProtocolCanonical("http://example.org/pd|1.0");

            StepInstance step = new StepInstance();
            step.setId(UUID.randomUUID());
            step.setActionId("action-2");
            step.setState(StepState.DUE);

            when(deviationRepository.save(any(Deviation.class)))
                    .thenAnswer(inv -> {
                        Deviation d = inv.getArgument(0);
                        d.setId(UUID.randomUUID());
                        return d;
                    });

            Deviation result = service.recordDeviation(pi, step, DeviationType.AMBIGUOUS, Map.of("matchCount", 3));

            assertThat(result.getDeviationType()).isEqualTo(DeviationType.AMBIGUOUS);
        }
    }

    @Nested
    @DisplayName("query methods")
    class QueryMethods {

        @Test
        void shouldFindByProtocolInstanceId() {
            UUID piId = UUID.randomUUID();
            when(deviationRepository.findByProtocolInstanceId(piId))
                    .thenReturn(List.of(new Deviation()));
            assertThat(service.findByProtocolInstanceId(piId)).hasSize(1);
        }

        @Test
        void shouldFindByDeviationType() {
            when(deviationRepository.findByDeviationType(DeviationType.MISSED))
                    .thenReturn(List.of(new Deviation()));
            assertThat(service.findByDeviationType(DeviationType.MISSED)).hasSize(1);
        }

        @Test
        void shouldFindByStepInstanceId() {
            UUID stepId = UUID.randomUUID();
            when(deviationRepository.findByStepInstanceId(stepId))
                    .thenReturn(List.of(new Deviation()));
            assertThat(service.findByStepInstanceId(stepId)).hasSize(1);
        }

        @Test
        void shouldCountByProtocolInstanceId() {
            UUID piId = UUID.randomUUID();
            when(deviationRepository.countByProtocolInstanceId(piId)).thenReturn(3L);
            assertThat(service.countByProtocolInstanceId(piId)).isEqualTo(3);
        }
    }
}
