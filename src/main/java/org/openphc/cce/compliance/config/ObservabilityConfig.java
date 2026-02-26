package org.openphc.cce.compliance.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.compliance.domain.repository.ProtocolInstanceRepository;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration for the CCE Compliance Service.
 * <p>
 * Registers custom metrics as specified in the CCE design:
 * <ul>
 *   <li>{@code cce.protocol.instances.active} — gauge of active protocol instances</li>
 * </ul>
 * <p>
 * Counter and timer metrics (cce.events.processed, cce.step.matching.duration, etc.)
 * are registered inline in {@link org.openphc.cce.compliance.service.ComplianceEngine}
 * and Kafka consumers.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "cce-compliance-service");
    }

    @Bean
    public Gauge activeProtocolInstancesGauge(MeterRegistry meterRegistry,
                                                ProtocolInstanceRepository repository) {
        return Gauge.builder("cce.protocol.instances.active",
                        () -> repository.countByStatus(ProtocolInstanceStatus.ACTIVE))
                .description("Number of active protocol instances")
                .register(meterRegistry);
    }

}
