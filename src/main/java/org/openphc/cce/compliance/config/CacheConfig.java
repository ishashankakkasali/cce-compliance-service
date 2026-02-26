package org.openphc.cce.compliance.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-based in-memory cache configuration.
 * <p>
 * Caches hot-path reference data that is read on every inbound event but
 * changes only when protocols are loaded/retired:
 * <ul>
 *   <li>{@code triggersByResourceType} — Tier 1 structural matches by resource type</li>
 *   <li>{@code triggersByResourceTypeAndCode} — Tier 1 structural matches by resource type + code</li>
 *   <li>{@code planDefinitionById} — PlanDefinition entities by UUID</li>
 *   <li>{@code activePlanDefinitions} — list of all active PlanDefinitions</li>
 * </ul>
 * <p>
 * All caches are evicted when protocols are loaded, retired, or deleted
 * via {@link org.openphc.cce.compliance.service.ProtocolDefinitionService}.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "triggersByResourceType",
                "triggersByResourceTypeAndCode",
                "planDefinitionById",
                "activePlanDefinitions"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats());
        return cacheManager;
    }
}
