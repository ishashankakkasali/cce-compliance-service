package org.openphc.cce.compliance.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.openphc.cce.compliance.domain.enums.PlanDefinitionStatus;
import org.openphc.cce.compliance.domain.repository.PlanDefinitionRepository;
import org.openphc.cce.compliance.domain.repository.TriggerIndexRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests that verify the full API flow using PostgreSQL Testcontainers
 * and embedded Kafka. Tests cover PlanDefinition CRUD, protocol tracking,
 * and endpoint error handling with real database interactions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "cce.events.inbound",
                "cce.scheduler.triggers",
                "cce.intelligence.triggers",
                "cce.protocol.control",
                "cce.deadletter"
        },
        brokerProperties = {"listeners=PLAINTEXT://localhost:0"}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Integration Tests")
class ComplianceServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cce_compliance_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
        // Disable Redis for integration tests
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private PlanDefinitionRepository planDefinitionRepository;
    @Autowired private TriggerIndexRepository triggerIndexRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        triggerIndexRepository.deleteAll();
        planDefinitionRepository.deleteAll();
    }

    @Nested
    @DisplayName("PlanDefinition API Integration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PlanDefinitionApiTests {

        private static final String VALID_PLAN_DEFINITION_JSON = """
                {
                    "resourceType": "PlanDefinition",
                    "url": "http://example.org/PlanDefinition/immunization-protocol",
                    "version": "1.0",
                    "status": "active",
                    "title": "Immunization Protocol",
                    "description": "A test immunization protocol",
                    "action": [
                        {
                            "id": "dose-1",
                            "title": "First Dose",
                            "trigger": [
                                {
                                    "type": "named-event",
                                    "name": "encounter-start",
                                    "condition": {
                                        "kind": "applicability",
                                        "expression": {
                                            "language": "text/fhirpath",
                                            "expression": "true"
                                        }
                                    }
                                }
                            ]
                        }
                    ]
                }
                """;

        @Test
        @Order(1)
        @DisplayName("POST /v1/protocol-definitions should load PlanDefinition")
        void shouldLoadPlanDefinition() throws Exception {
            // planDefinitionJson must be a JSON string, not a nested object
            String requestBody = objectMapper.writeValueAsString(
                    Map.of("planDefinitionJson", VALID_PLAN_DEFINITION_JSON));

            mockMvc.perform(post("/v1/protocol-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.url").value("http://example.org/PlanDefinition/immunization-protocol"))
                    .andExpect(jsonPath("$.version").value("1.0"))
                    .andExpect(jsonPath("$.status").value("active"));

            assertThat(planDefinitionRepository.count()).isEqualTo(1);
        }

        @Test
        @Order(2)
        @DisplayName("GET /v1/protocol-definitions should list all definitions")
        void shouldListDefinitions() throws Exception {
            // Insert a test record
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            entity.setUrl("http://example.org/pd/test-list");
            entity.setVersion("1.0");
            entity.setStatus(PlanDefinitionStatus.ACTIVE);
            entity.setLoadedAt(OffsetDateTime.now());
            entity.setDefinition(Map.of("resourceType", "PlanDefinition"));
            planDefinitionRepository.save(entity);

            mockMvc.perform(get("/v1/protocol-definitions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @Order(3)
        @DisplayName("GET /v1/protocol-definitions/{id} should return specific definition")
        void shouldGetById() throws Exception {
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            entity.setUrl("http://example.org/pd/by-id");
            entity.setVersion("2.0");
            entity.setStatus(PlanDefinitionStatus.ACTIVE);
            entity.setLoadedAt(OffsetDateTime.now());
            entity.setDefinition(Map.of("resourceType", "PlanDefinition"));
            entity = planDefinitionRepository.save(entity);

            mockMvc.perform(get("/v1/protocol-definitions/{id}", entity.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(entity.getId().toString()))
                    .andExpect(jsonPath("$.url").value("http://example.org/pd/by-id"));
        }

        @Test
        @Order(4)
        @DisplayName("GET /v1/protocol-definitions/{id} should return 404 for unknown id")
        void shouldReturn404ForUnknownId() throws Exception {
            mockMvc.perform(get("/v1/protocol-definitions/{id}", "00000000-0000-0000-0000-000000000099"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(5)
        @DisplayName("POST /v1/protocol-definitions/{id}/retire should retire a definition")
        void shouldRetireDefinition() throws Exception {
            PlanDefinitionEntity entity = new PlanDefinitionEntity();
            entity.setUrl("http://example.org/pd/retire-test");
            entity.setVersion("1.0");
            entity.setStatus(PlanDefinitionStatus.ACTIVE);
            entity.setLoadedAt(OffsetDateTime.now());
            entity.setDefinition(Map.of("resourceType", "PlanDefinition"));
            entity = planDefinitionRepository.save(entity);

            mockMvc.perform(post("/v1/protocol-definitions/{id}/retire", entity.getId()))
                    .andExpect(status().isNoContent());

            PlanDefinitionEntity retired = planDefinitionRepository.findById(entity.getId()).orElseThrow();
            assertThat(retired.getStatus()).isEqualTo(PlanDefinitionStatus.RETIRED);
        }
    }

    @Nested
    @DisplayName("Protocol Tracking API Integration")
    class ProtocolTrackingApiTests {

        @Test
        @DisplayName("GET /v1/patients/{patientId}/protocol-tracking should return empty list for unknown patient")
        void shouldReturnEmptyForUnknownPatient() throws Exception {
            mockMvc.perform(get("/v1/patients/{patientId}/protocol-tracking", "patient-unknown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("GET /v1/patients/{patientId}/protocol-tracking/active should return empty for unknown patient")
        void shouldReturnEmptyActiveForUnknownPatient() throws Exception {
            mockMvc.perform(get("/v1/patients/{patientId}/protocol-tracking/active", "patient-unknown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("GET /v1/patients/{patientId}/events should return paginated events")
        void shouldReturnPaginatedEvents() throws Exception {
            mockMvc.perform(get("/v1/patients/{patientId}/events", "patient-unknown")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("Actuator Endpoints")
    class ActuatorTests {

        @Test
        @DisplayName("Health endpoint should be available")
        void shouldReturnHealth() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }
}
