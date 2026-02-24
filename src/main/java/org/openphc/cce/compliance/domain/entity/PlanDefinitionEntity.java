package org.openphc.cce.compliance.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.openphc.cce.compliance.domain.enums.PlanDefinitionStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * PlanDefinition stored as queryable JSONB — the core protocol definition resource.
 * Maps FHIR PlanDefinition R4 resources stored with their full JSON representation.
 */
@Entity
@Table(name = "plan_definition", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"url", "version"})
})
public class PlanDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanDefinitionStatus status;

    @Type(JsonType.class)
    @Column(name = "definition", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> definition;

    @Column(name = "loaded_at", nullable = false)
    private OffsetDateTime loadedAt;

    public PlanDefinitionEntity() {
    }

    @PrePersist
    protected void onCreate() {
        if (loadedAt == null) {
            loadedAt = OffsetDateTime.now();
        }
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public PlanDefinitionStatus getStatus() {
        return status;
    }

    public void setStatus(PlanDefinitionStatus status) {
        this.status = status;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }

    public void setDefinition(Map<String, Object> definition) {
        this.definition = definition;
    }

    public OffsetDateTime getLoadedAt() {
        return loadedAt;
    }

    public void setLoadedAt(OffsetDateTime loadedAt) {
        this.loadedAt = loadedAt;
    }

    /**
     * Returns the canonical reference: url|version.
     */
    public String getCanonical() {
        return url + "|" + version;
    }
}
