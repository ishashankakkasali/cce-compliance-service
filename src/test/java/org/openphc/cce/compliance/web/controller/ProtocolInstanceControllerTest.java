package org.openphc.cce.compliance.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.domain.enums.PlanDefinitionStatus;
import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.compliance.service.ProtocolInstanceService;
import org.openphc.cce.compliance.web.GlobalExceptionHandler;
import org.openphc.cce.compliance.web.mapper.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProtocolInstanceController.class)
@Import({DtoMapper.class, GlobalExceptionHandler.class})
@DisplayName("ProtocolInstanceController Tests")
class ProtocolInstanceControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProtocolInstanceService protocolInstanceService;

    private ProtocolInstance createInstance(UUID id, String patientId) {
        return createInstance(id, patientId, null);
    }

    private ProtocolInstance createInstance(UUID id, String patientId, String facilityId) {
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
        pi.setFacilityId(facilityId);
        pi.setStatus(ProtocolInstanceStatus.ACTIVE);
        pi.setEnrolledAt(OffsetDateTime.now());
        pi.setCreatedAt(OffsetDateTime.now());
        pi.setUpdatedAt(OffsetDateTime.now());
        return pi;
    }

    @Nested
    @DisplayName("GET /v1/protocol-instances (list)")
    class ListInstances {

        @Test
        @DisplayName("should return paginated list without filters")
        void shouldReturnPaginatedList() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<ProtocolInstance> instances = List.of(
                    createInstance(id1, "Patient/123", "facility-A"),
                    createInstance(id2, "Patient/456", "facility-B")
            );
            Page<ProtocolInstance> page = new PageImpl<>(instances, PageRequest.of(0, 20), 2);

            when(protocolInstanceService.search(
                    eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/v1/protocol-instances"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].id").value(id1.toString()))
                    .andExpect(jsonPath("$.content[0].patientId").value("Patient/123"))
                    .andExpect(jsonPath("$.content[0].facilityId").value("facility-A"))
                    .andExpect(jsonPath("$.content[1].id").value(id2.toString()))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("should filter by patientId")
        void shouldFilterByPatientId() throws Exception {
            UUID id = UUID.randomUUID();
            ProtocolInstance pi = createInstance(id, "Patient/123");
            Page<ProtocolInstance> page = new PageImpl<>(List.of(pi), PageRequest.of(0, 20), 1);

            when(protocolInstanceService.search(
                    eq("Patient/123"), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/v1/protocol-instances").param("patientId", "Patient/123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].patientId").value("Patient/123"));
        }

        @Test
        @DisplayName("should filter by status")
        void shouldFilterByStatus() throws Exception {
            UUID id = UUID.randomUUID();
            ProtocolInstance pi = createInstance(id, "Patient/123");
            Page<ProtocolInstance> page = new PageImpl<>(List.of(pi), PageRequest.of(0, 20), 1);

            when(protocolInstanceService.search(
                    eq(null), eq(null), eq(ProtocolInstanceStatus.ACTIVE), eq(null), eq(null),
                    any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/v1/protocol-instances").param("status", "active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].status").value("active"));
        }

        @Test
        @DisplayName("should filter by facilityId")
        void shouldFilterByFacilityId() throws Exception {
            UUID id = UUID.randomUUID();
            ProtocolInstance pi = createInstance(id, "Patient/123", "facility-X");
            Page<ProtocolInstance> page = new PageImpl<>(List.of(pi), PageRequest.of(0, 20), 1);

            when(protocolInstanceService.search(
                    eq(null), eq(null), eq(null), eq("facility-X"), eq(null), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/v1/protocol-instances").param("facilityId", "facility-X"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].facilityId").value("facility-X"));
        }

        @Test
        @DisplayName("should filter by multiple criteria")
        void shouldFilterByMultipleCriteria() throws Exception {
            UUID id = UUID.randomUUID();
            ProtocolInstance pi = createInstance(id, "Patient/789", "facility-Y");
            Page<ProtocolInstance> page = new PageImpl<>(List.of(pi), PageRequest.of(0, 20), 1);

            when(protocolInstanceService.search(
                    eq("Patient/789"), eq(null), eq(ProtocolInstanceStatus.ACTIVE),
                    eq("facility-Y"), eq(null), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/v1/protocol-instances")
                            .param("patientId", "Patient/789")
                            .param("status", "active")
                            .param("facilityId", "facility-Y"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].patientId").value("Patient/789"))
                    .andExpect(jsonPath("$.content[0].facilityId").value("facility-Y"));
        }

        @Test
        @DisplayName("should return empty page when no results")
        void shouldReturnEmptyPage() throws Exception {
            Page<ProtocolInstance> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

            when(protocolInstanceService.search(
                    any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(emptyPage);

            mockMvc.perform(get("/v1/protocol-instances").param("patientId", "Patient/nonexistent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("should return 400 for invalid status value")
        void shouldReturn400ForInvalidStatus() throws Exception {
            mockMvc.perform(get("/v1/protocol-instances").param("status", "invalid_status"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /v1/protocol-instances/{id}")
    class GetById {

        @Test
        @DisplayName("should return protocol instance when found")
        void shouldReturnWhenFound() throws Exception {
            UUID id = UUID.randomUUID();
            ProtocolInstance pi = createInstance(id, "Patient/123");
            when(protocolInstanceService.findById(id)).thenReturn(Optional.of(pi));

            mockMvc.perform(get("/v1/protocol-instances/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.patientId").value("Patient/123"))
                    .andExpect(jsonPath("$.status").value("active"));
        }

        @Test
        @DisplayName("should return 404 when not found")
        void shouldReturn404() throws Exception {
            UUID id = UUID.randomUUID();
            when(protocolInstanceService.findById(id)).thenReturn(Optional.empty());

            mockMvc.perform(get("/v1/protocol-instances/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /v1/protocol-instances/{id}/complete")
    class Complete {

        @Test
        @DisplayName("should return 204 when completed")
        void shouldReturn204() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(post("/v1/protocol-instances/{id}/complete", id))
                    .andExpect(status().isNoContent());

            verify(protocolInstanceService).completeProtocol(id);
        }

        @Test
        @DisplayName("should return 400 when instance not found")
        void shouldReturn400WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(new IllegalArgumentException("not found")).when(protocolInstanceService).completeProtocol(id);

            mockMvc.perform(post("/v1/protocol-instances/{id}/complete", id))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/protocol-instances/{id}/withdraw")
    class Withdraw {

        @Test
        @DisplayName("should return 204 when withdrawn")
        void shouldReturn204() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(post("/v1/protocol-instances/{id}/withdraw", id))
                    .andExpect(status().isNoContent());

            verify(protocolInstanceService).withdrawProtocol(id);
        }
    }
}
