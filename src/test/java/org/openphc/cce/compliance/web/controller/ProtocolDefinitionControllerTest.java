package org.openphc.cce.compliance.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.openphc.cce.compliance.domain.enums.PlanDefinitionStatus;
import org.openphc.cce.compliance.service.ProtocolDefinitionService;
import org.openphc.cce.compliance.web.GlobalExceptionHandler;
import org.openphc.cce.compliance.web.dto.PlanDefinitionDto;
import org.openphc.cce.compliance.web.mapper.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProtocolDefinitionController.class)
@Import({DtoMapper.class, GlobalExceptionHandler.class})
@DisplayName("ProtocolDefinitionController Tests")
class ProtocolDefinitionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ProtocolDefinitionService protocolDefinitionService;

    private PlanDefinitionEntity createEntity(UUID id, String url, String version) {
        PlanDefinitionEntity entity = new PlanDefinitionEntity();
        entity.setId(id);
        entity.setUrl(url);
        entity.setVersion(version);
        entity.setStatus(PlanDefinitionStatus.ACTIVE);
        entity.setDefinition(Map.of("resourceType", "PlanDefinition", "url", url, "version", version));
        entity.setLoadedAt(OffsetDateTime.now());
        return entity;
    }

    @Nested
    @DisplayName("POST /v1/protocol-definitions")
    class LoadPlanDefinition {

        @Test
        @DisplayName("should return 201 Created when PlanDefinition is loaded")
        void shouldReturn201() throws Exception {
            UUID id = UUID.randomUUID();
            PlanDefinitionEntity entity = createEntity(id, "http://example.org/pd", "1.0");

            when(protocolDefinitionService.loadPlanDefinition(anyString())).thenReturn(entity);

            String requestBody = "{\"planDefinitionJson\":\"{\\\"resourceType\\\":\\\"PlanDefinition\\\"}\"}";

            mockMvc.perform(post("/v1/protocol-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.url").value("http://example.org/pd"))
                    .andExpect(jsonPath("$.version").value("1.0"))
                    .andExpect(jsonPath("$.status").value("active"));
        }

        @Test
        @DisplayName("should return 400 when planDefinitionJson is blank")
        void shouldReturn400WhenBlank() throws Exception {
            mockMvc.perform(post("/v1/protocol-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planDefinitionJson\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 409 Conflict when PlanDefinition already exists")
        void shouldReturn409WhenExists() throws Exception {
            when(protocolDefinitionService.loadPlanDefinition(anyString()))
                    .thenThrow(new IllegalStateException("PlanDefinition already exists"));

            mockMvc.perform(post("/v1/protocol-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planDefinitionJson\":\"{}\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        @DisplayName("should return 400 when JSON is invalid")
        void shouldReturn400WhenInvalidJson() throws Exception {
            when(protocolDefinitionService.loadPlanDefinition(anyString()))
                    .thenThrow(new IllegalArgumentException("Invalid JSON"));

            mockMvc.perform(post("/v1/protocol-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planDefinitionJson\":\"invalid\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /v1/protocol-definitions")
    class ListActive {

        @Test
        @DisplayName("should return list of active PlanDefinitions")
        void shouldReturnActiveList() throws Exception {
            PlanDefinitionEntity entity = createEntity(UUID.randomUUID(), "http://example.org/pd", "1.0");
            when(protocolDefinitionService.findAllActive()).thenReturn(List.of(entity));

            mockMvc.perform(get("/v1/protocol-definitions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].url").value("http://example.org/pd"));
        }

        @Test
        @DisplayName("should return empty list when none active")
        void shouldReturnEmptyList() throws Exception {
            when(protocolDefinitionService.findAllActive()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/v1/protocol-definitions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /v1/protocol-definitions/{id}")
    class GetById {

        @Test
        @DisplayName("should return PlanDefinition when found")
        void shouldReturnWhenFound() throws Exception {
            UUID id = UUID.randomUUID();
            PlanDefinitionEntity entity = createEntity(id, "http://example.org/pd", "1.0");
            when(protocolDefinitionService.findById(id)).thenReturn(Optional.of(entity));

            mockMvc.perform(get("/v1/protocol-definitions/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));
        }

        @Test
        @DisplayName("should return 404 when not found")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(protocolDefinitionService.findById(id)).thenReturn(Optional.empty());

            mockMvc.perform(get("/v1/protocol-definitions/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /v1/protocol-definitions/by-url")
    class GetByUrl {

        @Test
        @DisplayName("should return PlanDefinitions by URL")
        void shouldReturnByUrl() throws Exception {
            PlanDefinitionEntity entity = createEntity(UUID.randomUUID(), "http://example.org/pd", "1.0");
            when(protocolDefinitionService.findByUrl("http://example.org/pd")).thenReturn(List.of(entity));

            mockMvc.perform(get("/v1/protocol-definitions/by-url")
                            .param("url", "http://example.org/pd"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("GET /v1/protocol-definitions/by-url-version")
    class GetByUrlAndVersion {

        @Test
        @DisplayName("should return PlanDefinition by URL and version")
        void shouldReturnByUrlAndVersion() throws Exception {
            UUID id = UUID.randomUUID();
            PlanDefinitionEntity entity = createEntity(id, "http://example.org/pd", "2.0");
            when(protocolDefinitionService.findByUrlAndVersion("http://example.org/pd", "2.0"))
                    .thenReturn(Optional.of(entity));

            mockMvc.perform(get("/v1/protocol-definitions/by-url-version")
                            .param("url", "http://example.org/pd")
                            .param("version", "2.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value("2.0"));
        }

        @Test
        @DisplayName("should return 404 when not found by URL and version")
        void shouldReturn404WhenNotFound() throws Exception {
            when(protocolDefinitionService.findByUrlAndVersion("http://notfound.org", "1.0"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/v1/protocol-definitions/by-url-version")
                            .param("url", "http://notfound.org")
                            .param("version", "1.0"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /v1/protocol-definitions/{id}/retire")
    class Retire {

        @Test
        @DisplayName("should return 204 when retired")
        void shouldReturn204WhenRetired() throws Exception {
            UUID id = UUID.randomUUID();
            PlanDefinitionEntity entity = createEntity(id, "http://example.org/pd", "1.0");
            when(protocolDefinitionService.findById(id)).thenReturn(Optional.of(entity));

            mockMvc.perform(post("/v1/protocol-definitions/{id}/retire", id))
                    .andExpect(status().isNoContent());

            verify(protocolDefinitionService).retirePlanDefinition("http://example.org/pd", "1.0");
        }

        @Test
        @DisplayName("should return 404 when not found for retire")
        void shouldReturn404WhenNotFoundForRetire() throws Exception {
            UUID id = UUID.randomUUID();
            when(protocolDefinitionService.findById(id)).thenReturn(Optional.empty());

            mockMvc.perform(post("/v1/protocol-definitions/{id}/retire", id))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /v1/protocol-definitions/{id}/rebuild-index")
    class RebuildIndex {

        @Test
        @DisplayName("should return 204 when index rebuilt")
        void shouldReturn204() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(post("/v1/protocol-definitions/{id}/rebuild-index", id))
                    .andExpect(status().isNoContent());

            verify(protocolDefinitionService).rebuildTriggerIndex(id);
        }
    }

    @Nested
    @DisplayName("DELETE /v1/protocol-definitions/{id}")
    class DeletePlanDefinition {

        @Test
        @DisplayName("should return 204 when deleted successfully")
        void shouldReturn204WhenDeleted() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(delete("/v1/protocol-definitions/{id}", id))
                    .andExpect(status().isNoContent());

            verify(protocolDefinitionService).deletePlanDefinition(id);
        }

        @Test
        @DisplayName("should return 404 when PlanDefinition not found")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(new NoSuchElementException("PlanDefinition not found: " + id))
                    .when(protocolDefinitionService).deletePlanDefinition(id);

            mockMvc.perform(delete("/v1/protocol-definitions/{id}", id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 409 when protocol instances still reference it")
        void shouldReturn409WhenInstancesExist() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(new IllegalStateException("Cannot delete PlanDefinition " + id + ": 3 protocol instance(s) still reference it"))
                    .when(protocolDefinitionService).deletePlanDefinition(id);

            mockMvc.perform(delete("/v1/protocol-definitions/{id}", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("Cannot delete")));
        }
    }
}
