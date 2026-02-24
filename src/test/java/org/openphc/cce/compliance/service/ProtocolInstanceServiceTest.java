package org.openphc.cce.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.domain.enums.PlanDefinitionStatus;
import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.compliance.domain.repository.ProtocolInstanceRepository;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProtocolInstanceService Tests")
class ProtocolInstanceServiceTest {

    @Mock private ProtocolInstanceRepository protocolInstanceRepository;

    private ProtocolInstanceService service;

    @BeforeEach
    void setUp() {
        service = new ProtocolInstanceService(protocolInstanceRepository);
    }

    private PlanDefinitionEntity createPd() {
        PlanDefinitionEntity pd = new PlanDefinitionEntity();
        pd.setId(UUID.randomUUID());
        pd.setUrl("http://example.org/pd");
        pd.setVersion("1.0");
        pd.setStatus(PlanDefinitionStatus.ACTIVE);
        return pd;
    }

    @Nested
    @DisplayName("enrollOrGetActive")
    class EnrollOrGetActive {

        @Test
        @DisplayName("should return existing active instance")
        void shouldReturnExistingActive() {
            PlanDefinitionEntity pd = createPd();
            ProtocolInstance existing = new ProtocolInstance();
            existing.setId(UUID.randomUUID());
            existing.setPatientId("Patient/123");
            existing.setStatus(ProtocolInstanceStatus.ACTIVE);

            when(protocolInstanceRepository.findActiveByPatientIdAndPlanDefinition("Patient/123", pd.getId()))
                    .thenReturn(List.of(existing));

            ProtocolInstance result = service.enrollOrGetActive("Patient/123", pd);

            assertThat(result.getId()).isEqualTo(existing.getId());
            verify(protocolInstanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create new instance when none exists")
        void shouldCreateNewInstance() {
            PlanDefinitionEntity pd = createPd();
            when(protocolInstanceRepository.findActiveByPatientIdAndPlanDefinition("Patient/456", pd.getId()))
                    .thenReturn(Collections.emptyList());
            when(protocolInstanceRepository.save(any(ProtocolInstance.class)))
                    .thenAnswer(inv -> {
                        ProtocolInstance pi = inv.getArgument(0);
                        pi.setId(UUID.randomUUID());
                        return pi;
                    });

            ProtocolInstance result = service.enrollOrGetActive("Patient/456", pd);

            assertThat(result.getPatientId()).isEqualTo("Patient/456");
            assertThat(result.getStatus()).isEqualTo(ProtocolInstanceStatus.ACTIVE);
            assertThat(result.getProtocolCanonical()).isEqualTo(pd.getCanonical());
            verify(protocolInstanceRepository).save(any());
        }
    }

    @Nested
    @DisplayName("completeProtocol")
    class CompleteProtocol {

        @Test
        @DisplayName("should mark protocol as completed")
        void shouldCompleteProtocol() {
            UUID id = UUID.randomUUID();
            ProtocolInstance instance = new ProtocolInstance();
            instance.setId(id);
            instance.setStatus(ProtocolInstanceStatus.ACTIVE);

            when(protocolInstanceRepository.findById(id)).thenReturn(Optional.of(instance));
            when(protocolInstanceRepository.save(any())).thenReturn(instance);

            service.completeProtocol(id);

            assertThat(instance.getStatus()).isEqualTo(ProtocolInstanceStatus.COMPLETED);
        }

        @Test
        @DisplayName("should throw when instance not found")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(protocolInstanceRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.completeProtocol(id))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("withdrawProtocol")
    class WithdrawProtocol {

        @Test
        @DisplayName("should mark protocol as withdrawn")
        void shouldWithdrawProtocol() {
            UUID id = UUID.randomUUID();
            ProtocolInstance instance = new ProtocolInstance();
            instance.setId(id);
            instance.setStatus(ProtocolInstanceStatus.ACTIVE);

            when(protocolInstanceRepository.findById(id)).thenReturn(Optional.of(instance));
            when(protocolInstanceRepository.save(any())).thenReturn(instance);

            service.withdrawProtocol(id);

            assertThat(instance.getStatus()).isEqualTo(ProtocolInstanceStatus.WITHDRAWN);
        }

        @Test
        @DisplayName("should throw when instance not found")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(protocolInstanceRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.withdrawProtocol(id))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("query methods")
    class QueryMethods {

        @Test
        void shouldFindByPatientId() {
            when(protocolInstanceRepository.findByPatientId("Patient/123"))
                    .thenReturn(List.of(new ProtocolInstance()));
            assertThat(service.findByPatientId("Patient/123")).hasSize(1);
        }

        @Test
        void shouldFindActiveByPatientId() {
            when(protocolInstanceRepository.findActiveByPatientId("Patient/123"))
                    .thenReturn(List.of(new ProtocolInstance()));
            assertThat(service.findActiveByPatientId("Patient/123")).hasSize(1);
        }

        @Test
        void shouldFindById() {
            UUID id = UUID.randomUUID();
            when(protocolInstanceRepository.findById(id)).thenReturn(Optional.of(new ProtocolInstance()));
            assertThat(service.findById(id)).isPresent();
        }

        @Test
        void shouldCountByStatus() {
            when(protocolInstanceRepository.countByStatus(ProtocolInstanceStatus.ACTIVE)).thenReturn(5L);
            assertThat(service.countByStatus(ProtocolInstanceStatus.ACTIVE)).isEqualTo(5);
        }
    }
}
