package org.openphc.cce.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.compliance.domain.entity.AuditLog;
import org.openphc.cce.compliance.domain.repository.AuditLogRepository;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Tests")
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(auditLogRepository);
    }

    @Nested
    @DisplayName("audit")
    class Audit {

        @Test
        @DisplayName("should create audit log entry with all fields")
        void shouldCreateAuditLogEntry() {
            when(auditLogRepository.save(any(AuditLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.audit("COMPLIANCE", "STEP_COMPLETED", "system",
                    "StepInstance", "step-123", Map.of("key", "value"), "127.0.0.1");

            verify(auditLogRepository).save(argThat(log ->
                    "COMPLIANCE".equals(log.getEventCategory())
                            && "STEP_COMPLETED".equals(log.getEventType())
                            && "system".equals(log.getActor())
                            && "StepInstance".equals(log.getResourceType())
                            && "step-123".equals(log.getResourceId())
                            && "127.0.0.1".equals(log.getIpAddress())
                            && log.getTimestamp() != null
            ));
        }
    }

    @Nested
    @DisplayName("auditSystem")
    class AuditSystem {

        @Test
        @DisplayName("should create system audit log with actor=system and no IP")
        void shouldCreateSystemAudit() {
            when(auditLogRepository.save(any(AuditLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.auditSystem("COMPLIANCE", "PROTOCOL_ENROLLED",
                    "ProtocolInstance", "pi-123", Map.of("patientId", "Patient/123"));

            verify(auditLogRepository).save(argThat(log ->
                    "system".equals(log.getActor())
                            && log.getIpAddress() == null
            ));
        }
    }
}
