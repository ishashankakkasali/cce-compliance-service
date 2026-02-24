package org.openphc.cce.compliance.web.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openphc.cce.compliance.domain.entity.*;
import org.openphc.cce.compliance.domain.enums.*;
import org.openphc.cce.compliance.service.*;
import org.openphc.cce.compliance.web.GlobalExceptionHandler;
import org.openphc.cce.compliance.web.mapper.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProtocolTrackingController.class)
@Import({DtoMapper.class, GlobalExceptionHandler.class})
@DisplayName("ProtocolTrackingController Tests")
class ProtocolTrackingControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProtocolInstanceService protocolInstanceService;
    @MockitoBean private StepInstanceService stepInstanceService;
    @MockitoBean private DeviationService deviationService;
    @MockitoBean private EventLogService eventLogService;

    private ProtocolInstance createInstance(UUID id, String patientId) {
        PlanDefinitionEntity pd = new PlanDefinitionEntity();
        pd.setId(UUID.randomUUID());
        pd.setUrl("http://example.org/pd");
        pd.setVersion("1.0");
        pd.setStatus(PlanDefinitionStatus.ACTIVE);

        ProtocolInstance pi = new ProtocolInstance();
        pi.setId(id);
        pi.setPatientId(patientId);
        pi.setProtocolCanonical("http://example.org/pd|1.0");
        pi.setPlanDefinition(pd);
        pi.setStatus(ProtocolInstanceStatus.ACTIVE);
        pi.setEnrolledAt(OffsetDateTime.now());
        pi.setCreatedAt(OffsetDateTime.now());
        pi.setUpdatedAt(OffsetDateTime.now());
        return pi;
    }

    @Nested
    @DisplayName("GET /v1/patients/{patientId}/protocol-tracking")
    class ListProtocols {

        @Test
        @DisplayName("should return patient protocol instances")
        void shouldReturnPatientProtocols() throws Exception {
            ProtocolInstance pi = createInstance(UUID.randomUUID(), "patient-123");
            when(protocolInstanceService.findByPatientId("patient-123")).thenReturn(List.of(pi));

            mockMvc.perform(get("/v1/patients/{patientId}/protocol-tracking", "patient-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].patientId").value("patient-123"));
        }

        @Test
        @DisplayName("should return empty list for patient with no protocols")
        void shouldReturnEmptyList() throws Exception {
            when(protocolInstanceService.findByPatientId("patient-999")).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/v1/patients/{patientId}/protocol-tracking", "patient-999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /v1/patients/{patientId}/protocol-tracking/active")
    class ListActiveProtocols {

        @Test
        @DisplayName("should return active protocol instances")
        void shouldReturnActiveProtocols() throws Exception {
            ProtocolInstance pi = createInstance(UUID.randomUUID(), "patient-123");
            when(protocolInstanceService.findActiveByPatientId("patient-123")).thenReturn(List.of(pi));

            mockMvc.perform(get("/v1/patients/{patientId}/protocol-tracking/active", "patient-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("GET /v1/patients/{patientId}/protocol-tracking/{protocolInstanceId}")
    class GetProtocolDetail {

        @Test
        @DisplayName("should return protocol detail for the correct patient")
        void shouldReturnProtocolDetail() throws Exception {
            UUID piId = UUID.randomUUID();
            ProtocolInstance pi = createInstance(piId, "patient-123");
            when(protocolInstanceService.findById(piId)).thenReturn(Optional.of(pi));

            mockMvc.perform(get("/v1/patients/{patientId}/protocol-tracking/{piId}",
                            "patient-123", piId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(piId.toString()));
        }

        @Test
        @DisplayName("should return 404 when patient doesn't match")
        void shouldReturn404WhenPatientDoesNotMatch() throws Exception {
            UUID piId = UUID.randomUUID();
            ProtocolInstance pi = createInstance(piId, "patient-456");
            when(protocolInstanceService.findById(piId)).thenReturn(Optional.of(pi));

            mockMvc.perform(get("/v1/patients/{patientId}/protocol-tracking/{piId}",
                            "patient-123", piId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /v1/patients/{patientId}/protocol-tracking/{piId}/steps")
    class ListSteps {

        @Test
        @DisplayName("should return step instances")
        void shouldReturnSteps() throws Exception {
            UUID piId = UUID.randomUUID();
            StepInstance step = new StepInstance();
            step.setId(UUID.randomUUID());
            step.setActionId("action-1");
            step.setState(StepState.DUE);
            step.setRepeatIndex(0);
            step.setCreatedAt(OffsetDateTime.now());
            step.setUpdatedAt(OffsetDateTime.now());

            when(stepInstanceService.findByProtocolInstanceId(piId)).thenReturn(List.of(step));

            mockMvc.perform(get("/v1/patients/{patientId}/protocol-tracking/{piId}/steps",
                            "patient-123", piId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].actionId").value("action-1"))
                    .andExpect(jsonPath("$[0].state").value("due"));
        }
    }

    @Nested
    @DisplayName("GET /v1/patients/{patientId}/protocol-tracking/{piId}/deviations")
    class ListDeviations {

        @Test
        @DisplayName("should return deviation list")
        void shouldReturnDeviations() throws Exception {
            UUID piId = UUID.randomUUID();
            Deviation deviation = new Deviation();
            deviation.setId(UUID.randomUUID());
            deviation.setDeviationType(DeviationType.OVERDUE);
            deviation.setDetectedAt(OffsetDateTime.now());

            when(deviationService.findByProtocolInstanceId(piId)).thenReturn(List.of(deviation));

            mockMvc.perform(get("/v1/patients/{patientId}/protocol-tracking/{piId}/deviations",
                            "patient-123", piId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].deviationType").value("overdue"));
        }
    }

    @Nested
    @DisplayName("GET /v1/patients/{patientId}/events")
    class ListPatientEvents {

        @Test
        @DisplayName("should return paginated patient events")
        void shouldReturnPaginatedEvents() throws Exception {
            EventLog eventLog = new EventLog();
            eventLog.setId(UUID.randomUUID());
            eventLog.setCloudeventsId("ce-1");
            eventLog.setSource("source-1");
            eventLog.setSubject("patient-123");
            eventLog.setProcessingStatus(ProcessingStatus.MATCHED);
            eventLog.setEventTime(OffsetDateTime.now());
            eventLog.setReceivedAt(OffsetDateTime.now());
            eventLog.setData(Map.of());

            Page<EventLog> page = new PageImpl<>(List.of(eventLog));
            when(eventLogService.findBySubject(eq("patient-123"), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/v1/patients/{patientId}/events", "patient-123")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].cloudeventsId").value("ce-1"));
        }
    }
}
