package org.openphc.cce.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hl7.fhir.r4.model.DataRequirement;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.TriggerDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.openphc.cce.compliance.domain.entity.TriggerIndex;
import org.openphc.cce.compliance.domain.enums.PlanDefinitionStatus;
import org.openphc.cce.compliance.domain.enums.TriggerMode;
import org.openphc.cce.compliance.domain.repository.PlanDefinitionRepository;
import org.openphc.cce.compliance.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.compliance.domain.repository.TriggerIndexRepository;
import org.openphc.cce.compliance.fhir.PlanDefinitionParser;
import org.openphc.cce.compliance.fhir.PlanDefinitionParser.CodeFilter;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProtocolDefinitionService Tests")
class ProtocolDefinitionServiceTest {

    @Mock private PlanDefinitionRepository planDefinitionRepository;
    @Mock private TriggerIndexRepository triggerIndexRepository;
    @Mock private ProtocolInstanceRepository protocolInstanceRepository;
    @Mock private PlanDefinitionParser planDefinitionParser;
    @Mock private ObjectMapper objectMapper;
    @Mock private EntityManager entityManager;

    private ProtocolDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new ProtocolDefinitionService(
                planDefinitionRepository, triggerIndexRepository,
                protocolInstanceRepository,
                planDefinitionParser, objectMapper, entityManager);
    }

    @Nested
    @DisplayName("loadPlanDefinition")
    class LoadPlanDefinition {

        @Test
        @DisplayName("should load a valid PlanDefinition and build trigger index")
        void shouldLoadValidPlanDefinition() throws Exception {
            // Given
            String json = "{\"resourceType\":\"PlanDefinition\",\"url\":\"http://example.org/pd\",\"version\":\"1.0\"}";
            PlanDefinition parsed = new PlanDefinition();
            parsed.setUrl("http://example.org/pd");
            parsed.setVersion("1.0");

            when(planDefinitionParser.parse(json)).thenReturn(parsed);
            when(planDefinitionRepository.existsByUrlAndVersion("http://example.org/pd", "1.0")).thenReturn(false);
            when(objectMapper.readValue(eq(json), eq(Map.class))).thenReturn(Map.of("resourceType", "PlanDefinition"));
            when(planDefinitionRepository.save(any(PlanDefinitionEntity.class)))
                    .thenAnswer(inv -> {
                        PlanDefinitionEntity e = inv.getArgument(0);
                        e.setId(UUID.randomUUID());
                        return e;
                    });
            when(planDefinitionParser.extractAllActions(parsed)).thenReturn(Collections.emptyList());

            // When
            PlanDefinitionEntity result = service.loadPlanDefinition(json);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUrl()).isEqualTo("http://example.org/pd");
            assertThat(result.getVersion()).isEqualTo("1.0");
            assertThat(result.getStatus()).isEqualTo(PlanDefinitionStatus.ACTIVE);
            verify(planDefinitionRepository).save(any());
        }

        @Test
        @DisplayName("should throw when PlanDefinition has no URL")
        void shouldThrowWhenNoUrl() {
            String json = "{}";
            PlanDefinition parsed = new PlanDefinition();
            parsed.setVersion("1.0");
            when(planDefinitionParser.parse(json)).thenReturn(parsed);

            assertThatThrownBy(() -> service.loadPlanDefinition(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("url and version");
        }

        @Test
        @DisplayName("should throw when PlanDefinition has no version")
        void shouldThrowWhenNoVersion() {
            String json = "{}";
            PlanDefinition parsed = new PlanDefinition();
            parsed.setUrl("http://example.org/pd");
            when(planDefinitionParser.parse(json)).thenReturn(parsed);

            assertThatThrownBy(() -> service.loadPlanDefinition(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("url and version");
        }

        @Test
        @DisplayName("should throw when PlanDefinition already exists")
        void shouldThrowWhenAlreadyExists() {
            String json = "{}";
            PlanDefinition parsed = new PlanDefinition();
            parsed.setUrl("http://example.org/pd");
            parsed.setVersion("1.0");
            when(planDefinitionParser.parse(json)).thenReturn(parsed);
            when(planDefinitionRepository.existsByUrlAndVersion("http://example.org/pd", "1.0"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.loadPlanDefinition(json))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should throw when JSON is invalid")
        void shouldThrowWhenInvalidJson() throws Exception {
            String json = "not-json";
            PlanDefinition parsed = new PlanDefinition();
            parsed.setUrl("http://example.org/pd");
            parsed.setVersion("1.0");
            when(planDefinitionParser.parse(json)).thenReturn(parsed);
            when(planDefinitionRepository.existsByUrlAndVersion(anyString(), anyString())).thenReturn(false);
            when(objectMapper.readValue(eq(json), eq(Map.class)))
                    .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad"));

            assertThatThrownBy(() -> service.loadPlanDefinition(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid PlanDefinition JSON");
        }

        @Test
        @DisplayName("should build trigger index with code filters")
        void shouldBuildTriggerIndexWithCodeFilters() throws Exception {
            String json = "{\"resourceType\":\"PlanDefinition\",\"url\":\"http://example.org/pd\",\"version\":\"1.0\"}";
            PlanDefinition parsed = new PlanDefinition();
            parsed.setUrl("http://example.org/pd");
            parsed.setVersion("1.0");

            PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
            action.setId("action-1");

            TriggerDefinition trigger = new TriggerDefinition();
            trigger.setType(TriggerDefinition.TriggerType.DATAADDED);
            DataRequirement dr = new DataRequirement();
            dr.setType("Encounter");
            trigger.addData(dr);

            when(planDefinitionParser.parse(json)).thenReturn(parsed);
            when(planDefinitionRepository.existsByUrlAndVersion(anyString(), anyString())).thenReturn(false);
            when(objectMapper.readValue(eq(json), eq(Map.class))).thenReturn(Map.of());
            when(planDefinitionRepository.save(any())).thenAnswer(inv -> {
                PlanDefinitionEntity e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });
            when(planDefinitionParser.extractAllActions(parsed)).thenReturn(List.of(action));
            when(planDefinitionParser.extractTriggers(action)).thenReturn(List.of(trigger));
            when(planDefinitionParser.extractResourceType(trigger)).thenReturn("Encounter");
            when(planDefinitionParser.extractCodeFilters(trigger))
                    .thenReturn(List.of(new CodeFilter("http://loinc.org", "12345-6")));

            service.loadPlanDefinition(json);

            verify(entityManager).persist(argThat(obj -> {
                if (obj instanceof TriggerIndex ti) {
                    return "Encounter".equals(ti.getResourceType())
                            && "http://loinc.org".equals(ti.getCodeSystem())
                            && "12345-6".equals(ti.getCodeValue());
                }
                return false;
            }));
        }
    }

    @Nested
    @DisplayName("retirePlanDefinition")
    class RetirePlanDefinition {

        @Test
        @DisplayName("should retire existing PlanDefinition")
        void shouldRetireExisting() {
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            entity.setId(UUID.randomUUID());
            entity.setUrl("http://example.org/pd");
            entity.setVersion("1.0");
            entity.setStatus(PlanDefinitionStatus.ACTIVE);

            when(planDefinitionRepository.findByUrlAndVersion("http://example.org/pd", "1.0"))
                    .thenReturn(Optional.of(entity));
            when(planDefinitionRepository.save(any())).thenReturn(entity);

            service.retirePlanDefinition("http://example.org/pd", "1.0");

            assertThat(entity.getStatus()).isEqualTo(PlanDefinitionStatus.RETIRED);
            verify(triggerIndexRepository).deleteByPlanDefinitionId(entity.getId());
        }

        @Test
        @DisplayName("should throw when PlanDefinition not found")
        void shouldThrowWhenNotFound() {
            when(planDefinitionRepository.findByUrlAndVersion("http://notfound.org", "1.0"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.retirePlanDefinition("http://notfound.org", "1.0"))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("query methods")
    class QueryMethods {

        @Test
        void shouldFindById() {
            UUID id = UUID.randomUUID();
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            when(planDefinitionRepository.findById(id)).thenReturn(Optional.of(entity));

            Optional<PlanDefinitionEntity> result = service.findById(id);
            assertThat(result).isPresent();
        }

        @Test
        void shouldFindByUrlAndVersion() {
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            when(planDefinitionRepository.findByUrlAndVersion("url", "v1"))
                    .thenReturn(Optional.of(entity));

            Optional<PlanDefinitionEntity> result = service.findByUrlAndVersion("url", "v1");
            assertThat(result).isPresent();
        }

        @Test
        void shouldFindAllActive() {
            when(planDefinitionRepository.findByStatus(PlanDefinitionStatus.ACTIVE))
                    .thenReturn(List.of(new PlanDefinitionEntity()));
            assertThat(service.findAllActive()).hasSize(1);
        }

        @Test
        void shouldFindByUrl() {
            when(planDefinitionRepository.findByUrl("url"))
                    .thenReturn(List.of(new PlanDefinitionEntity()));
            assertThat(service.findByUrl("url")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("deletePlanDefinition")
    class DeletePlanDefinition {

        @Test
        @DisplayName("should delete PlanDefinition when no instances reference it")
        void shouldDeleteWhenNoInstances() {
            UUID id = UUID.randomUUID();
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            entity.setId(id);
            entity.setUrl("http://example.org/pd");
            entity.setVersion("1.0");
            entity.setStatus(PlanDefinitionStatus.ACTIVE);

            when(planDefinitionRepository.findById(id)).thenReturn(Optional.of(entity));
            when(protocolInstanceRepository.countByPlanDefinitionId(id)).thenReturn(0L);

            service.deletePlanDefinition(id);

            verify(triggerIndexRepository).deleteByPlanDefinitionId(id);
            verify(planDefinitionRepository).delete(entity);
        }

        @Test
        @DisplayName("should throw when PlanDefinition not found")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(planDefinitionRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deletePlanDefinition(id))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(id.toString());
        }

        @Test
        @DisplayName("should throw when protocol instances still reference the PlanDefinition")
        void shouldThrowWhenInstancesExist() {
            UUID id = UUID.randomUUID();
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            entity.setId(id);

            when(planDefinitionRepository.findById(id)).thenReturn(Optional.of(entity));
            when(protocolInstanceRepository.countByPlanDefinitionId(id)).thenReturn(3L);

            assertThatThrownBy(() -> service.deletePlanDefinition(id))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot delete")
                    .hasMessageContaining("3 protocol instance(s)");

            verify(planDefinitionRepository, never()).delete(any());
            verify(triggerIndexRepository, never()).deleteByPlanDefinitionId(any());
        }
    }

    @Nested
    @DisplayName("rebuildTriggerIndex")
    class RebuildTriggerIndex {

        @Test
        @DisplayName("should rebuild trigger index for existing PlanDefinition")
        void shouldRebuildIndex() throws Exception {
            UUID id = UUID.randomUUID();
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            entity.setId(id);
            entity.setDefinition(Map.of("resourceType", "PlanDefinition"));

            PlanDefinition parsed = new PlanDefinition();

            when(planDefinitionRepository.findById(id)).thenReturn(Optional.of(entity));
            when(objectMapper.writeValueAsString(entity.getDefinition())).thenReturn("{}");
            when(planDefinitionParser.parse("{}")).thenReturn(parsed);
            when(planDefinitionParser.extractAllActions(parsed)).thenReturn(Collections.emptyList());

            service.rebuildTriggerIndex(id);

            verify(triggerIndexRepository).deleteByPlanDefinitionId(id);
        }

        @Test
        @DisplayName("should throw when PlanDefinition not found for rebuild")
        void shouldThrowOnNotFound() {
            UUID id = UUID.randomUUID();
            when(planDefinitionRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rebuildTriggerIndex(id))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }
}
