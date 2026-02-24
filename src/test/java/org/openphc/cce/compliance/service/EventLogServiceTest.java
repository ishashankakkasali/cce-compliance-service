package org.openphc.cce.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.domain.entity.EventLog;
import org.openphc.cce.compliance.domain.enums.ProcessingStatus;
import org.openphc.cce.compliance.domain.repository.EventLogRepository;
import org.openphc.cce.compliance.kafka.model.CloudEventMessage;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventLogService Tests")
class EventLogServiceTest {

    @Mock private EventLogRepository eventLogRepository;

    private EventLogService service;

    @BeforeEach
    void setUp() {
        service = new EventLogService(eventLogRepository);
    }

    @Nested
    @DisplayName("isDuplicate")
    class IsDuplicate {

        @Test
        @DisplayName("should return true when event already exists")
        void shouldReturnTrueForDuplicate() {
            when(eventLogRepository.existsByCloudeventsIdAndSource("ce-1", "source-1"))
                    .thenReturn(true);

            assertThat(service.isDuplicate("ce-1", "source-1")).isTrue();
        }

        @Test
        @DisplayName("should return false when event does not exist")
        void shouldReturnFalseForNew() {
            when(eventLogRepository.existsByCloudeventsIdAndSource("ce-2", "source-1"))
                    .thenReturn(false);

            assertThat(service.isDuplicate("ce-2", "source-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("recordEvent")
    class RecordEvent {

        @Test
        @DisplayName("should record an event with all fields")
        void shouldRecordEventWithAllFields() {
            CloudEventMessage event = new CloudEventMessage();
            event.setId("ce-1");
            event.setSource("source-1");
            event.setSourceEventId("src-event-1");
            event.setSubject("Patient/123");
            event.setType("org.openhie.cr.encounter");
            event.setTime(OffsetDateTime.now());
            event.setCorrelationId("corr-1");
            event.setData(Map.of("resourceType", "Encounter"));
            event.setFacilityId("facility-1");

            when(eventLogRepository.save(any(EventLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            EventLog result = service.recordEvent(event, ProcessingStatus.ZERO_MATCH);

            assertThat(result.getCloudeventsId()).isEqualTo("ce-1");
            assertThat(result.getSource()).isEqualTo("source-1");
            assertThat(result.getSubject()).isEqualTo("Patient/123");
            assertThat(result.getProcessingStatus()).isEqualTo(ProcessingStatus.ZERO_MATCH);
            assertThat(result.getFacilityId()).isEqualTo("facility-1");
            verify(eventLogRepository).save(any(EventLog.class));
        }

        @Test
        @DisplayName("should use empty map when data is null")
        void shouldUseEmptyMapWhenDataNull() {
            CloudEventMessage event = new CloudEventMessage();
            event.setId("ce-2");
            event.setSource("source-2");

            when(eventLogRepository.save(any(EventLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            EventLog result = service.recordEvent(event, ProcessingStatus.ZERO_MATCH);

            assertThat(result.getData()).isEmpty();
        }

        @Test
        @DisplayName("should use current time when event time is null")
        void shouldUseCurrentTimeWhenEventTimeNull() {
            CloudEventMessage event = new CloudEventMessage();
            event.setId("ce-3");
            event.setSource("source-3");

            when(eventLogRepository.save(any(EventLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            EventLog result = service.recordEvent(event, ProcessingStatus.MATCHED);

            assertThat(result.getEventTime()).isNotNull();
            assertThat(result.getReceivedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("updateMatchResult")
    class UpdateMatchResult {

        @Test
        @DisplayName("should update event log with match details")
        void shouldUpdateWithMatchDetails() {
            UUID eventLogId = UUID.randomUUID();
            UUID piId = UUID.randomUUID();
            UUID pdId = UUID.randomUUID();
            UUID stepId = UUID.randomUUID();
            EventLog entry = new EventLog();

            when(eventLogRepository.findById(eventLogId)).thenReturn(Optional.of(entry));
            when(eventLogRepository.save(any())).thenReturn(entry);

            service.updateMatchResult(eventLogId, piId, pdId, "action-1", stepId, ProcessingStatus.MATCHED);

            assertThat(entry.getProtocolInstanceId()).isEqualTo(piId);
            assertThat(entry.getProtocolDefinitionId()).isEqualTo(pdId);
            assertThat(entry.getActionId()).isEqualTo("action-1");
            assertThat(entry.getMatchedStepInstanceId()).isEqualTo(stepId);
            assertThat(entry.getProcessingStatus()).isEqualTo(ProcessingStatus.MATCHED);
        }

        @Test
        @DisplayName("should do nothing when event log not found")
        void shouldDoNothingWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(eventLogRepository.findById(id)).thenReturn(Optional.empty());

            service.updateMatchResult(id, null, null, null, null, ProcessingStatus.MATCHED);

            verify(eventLogRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("query methods")
    class QueryMethods {

        @Test
        void shouldFindBySubject() {
            when(eventLogRepository.findBySubject("Patient/123"))
                    .thenReturn(List.of(new EventLog()));
            assertThat(service.findBySubject("Patient/123")).hasSize(1);
        }

        @Test
        void shouldFindById() {
            UUID id = UUID.randomUUID();
            when(eventLogRepository.findById(id)).thenReturn(Optional.of(new EventLog()));
            assertThat(service.findById(id)).isPresent();
        }
    }
}
