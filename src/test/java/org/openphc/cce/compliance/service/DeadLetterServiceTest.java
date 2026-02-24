package org.openphc.cce.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.domain.entity.DeadLetterEvent;
import org.openphc.cce.compliance.domain.enums.FailureStage;
import org.openphc.cce.compliance.domain.repository.DeadLetterEventRepository;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeadLetterService Tests")
class DeadLetterServiceTest {

    @Mock private DeadLetterEventRepository deadLetterEventRepository;

    private DeadLetterService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterService(deadLetterEventRepository);
    }

    @Nested
    @DisplayName("recordDeadLetter")
    class RecordDeadLetter {

        @Test
        @DisplayName("should record dead letter from Map payload")
        void shouldRecordFromMapPayload() {
            Map<String, Object> payload = Map.of("key", "value");

            when(deadLetterEventRepository.save(any(DeadLetterEvent.class)))
                    .thenAnswer(inv -> {
                        DeadLetterEvent e = inv.getArgument(0);
                        e.setId(UUID.randomUUID());
                        return e;
                    });

            DeadLetterEvent result = service.recordDeadLetter(payload, "processing failed", FailureStage.PROCESSING);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getFailureReason()).isEqualTo("processing failed");
            assertThat(result.getFailureStage()).isEqualTo(FailureStage.PROCESSING);
            assertThat(result.getRetryCount()).isEqualTo(0);
            assertThat(result.isResolved()).isFalse();
            assertThat(result.getPayload()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("should record dead letter from non-Map payload")
        void shouldRecordFromNonMapPayload() {
            when(deadLetterEventRepository.save(any(DeadLetterEvent.class)))
                    .thenAnswer(inv -> {
                        DeadLetterEvent e = inv.getArgument(0);
                        e.setId(UUID.randomUUID());
                        return e;
                    });

            DeadLetterEvent result = service.recordDeadLetter("raw-string", "validation failed", FailureStage.VALIDATION);

            assertThat(result.getPayload()).containsEntry("raw", "raw-string");
        }
    }

    @Nested
    @DisplayName("resolveDeadLetter")
    class ResolveDeadLetter {

        @Test
        @DisplayName("should mark dead letter as resolved")
        void shouldResolve() {
            UUID id = UUID.randomUUID();
            DeadLetterEvent event = new DeadLetterEvent();
            event.setId(id);
            event.setResolved(false);

            when(deadLetterEventRepository.findById(id)).thenReturn(Optional.of(event));
            when(deadLetterEventRepository.save(any())).thenReturn(event);

            service.resolveDeadLetter(id);

            assertThat(event.isResolved()).isTrue();
            assertThat(event.getResolvedAt()).isNotNull();
        }

        @Test
        @DisplayName("should do nothing when not found")
        void shouldDoNothingWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(deadLetterEventRepository.findById(id)).thenReturn(Optional.empty());

            service.resolveDeadLetter(id);

            verify(deadLetterEventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("incrementRetry")
    class IncrementRetry {

        @Test
        @DisplayName("should increment retry count with exponential backoff")
        void shouldIncrementRetryCount() {
            UUID id = UUID.randomUUID();
            DeadLetterEvent event = new DeadLetterEvent();
            event.setId(id);
            event.setRetryCount(0);

            when(deadLetterEventRepository.findById(id)).thenReturn(Optional.of(event));
            when(deadLetterEventRepository.save(any())).thenReturn(event);

            service.incrementRetry(id);

            assertThat(event.getRetryCount()).isEqualTo(1);
            assertThat(event.getNextRetryAt()).isNotNull();
        }

        @Test
        @DisplayName("should apply exponential backoff on second retry")
        void shouldApplyExponentialBackoff() {
            UUID id = UUID.randomUUID();
            DeadLetterEvent event = new DeadLetterEvent();
            event.setId(id);
            event.setRetryCount(2);

            when(deadLetterEventRepository.findById(id)).thenReturn(Optional.of(event));
            when(deadLetterEventRepository.save(any())).thenReturn(event);

            service.incrementRetry(id);

            assertThat(event.getRetryCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("query methods")
    class QueryMethods {

        @Test
        void shouldFindRetryableEvents() {
            when(deadLetterEventRepository.findRetryableEvents(any(OffsetDateTime.class)))
                    .thenReturn(List.of(new DeadLetterEvent()));
            assertThat(service.findRetryableEvents()).hasSize(1);
        }

        @Test
        void shouldFindUnresolved() {
            when(deadLetterEventRepository.findByResolvedFalse())
                    .thenReturn(List.of(new DeadLetterEvent()));
            assertThat(service.findUnresolved()).hasSize(1);
        }

        @Test
        void shouldCountUnresolved() {
            when(deadLetterEventRepository.countByResolvedFalse()).thenReturn(7L);
            assertThat(service.countUnresolved()).isEqualTo(7);
        }
    }
}
