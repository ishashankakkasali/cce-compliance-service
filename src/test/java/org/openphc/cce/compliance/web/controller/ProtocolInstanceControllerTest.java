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
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;

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
