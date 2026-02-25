package org.openphc.cce.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.openphc.cce.compliance.domain.entity.TriggerIndex;
import org.openphc.cce.compliance.domain.enums.PlanDefinitionStatus;
import org.openphc.cce.compliance.domain.enums.TriggerMode;
import org.openphc.cce.compliance.domain.repository.PlanDefinitionRepository;
import org.openphc.cce.compliance.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.compliance.domain.repository.TriggerIndexRepository;
import org.openphc.cce.compliance.fhir.PlanDefinitionParser;
import org.openphc.cce.compliance.fhir.PlanDefinitionParser.CodeFilter;
import org.hl7.fhir.r4.model.TriggerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Manages PlanDefinition lifecycle — loading, activation, retirement, and trigger index rebuilding.
 */
@Service
@Transactional
public class ProtocolDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(ProtocolDefinitionService.class);

    private final PlanDefinitionRepository planDefinitionRepository;
    private final TriggerIndexRepository triggerIndexRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final PlanDefinitionParser planDefinitionParser;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public ProtocolDefinitionService(PlanDefinitionRepository planDefinitionRepository,
                                      TriggerIndexRepository triggerIndexRepository,
                                      ProtocolInstanceRepository protocolInstanceRepository,
                                      PlanDefinitionParser planDefinitionParser,
                                      ObjectMapper objectMapper,
                                      EntityManager entityManager) {
        this.planDefinitionRepository = planDefinitionRepository;
        this.triggerIndexRepository = triggerIndexRepository;
        this.protocolInstanceRepository = protocolInstanceRepository;
        this.planDefinitionParser = planDefinitionParser;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    /**
     * Loads a PlanDefinition from its FHIR JSON representation.
     * Parses triggers and builds the trigger index.
     *
     * @param planDefinitionJson the FHIR PlanDefinition JSON
     * @return the saved PlanDefinitionEntity
     */
    @SuppressWarnings("unchecked")
    public PlanDefinitionEntity loadPlanDefinition(String planDefinitionJson) {
        PlanDefinition parsed = planDefinitionParser.parse(planDefinitionJson);

        String url = parsed.getUrl();
        String version = parsed.getVersion();

        if (url == null || version == null) {
            throw new IllegalArgumentException("PlanDefinition must have url and version");
        }

        // Check for existing version
        if (planDefinitionRepository.existsByUrlAndVersion(url, version)) {
            throw new IllegalStateException(
                    "PlanDefinition already exists: " + url + "|" + version);
        }

        // Convert FHIR resource to a Map for JSONB storage
        Map<String, Object> definitionMap;
        try {
            definitionMap = objectMapper.readValue(planDefinitionJson, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid PlanDefinition JSON", e);
        }

        PlanDefinitionEntity entity = new PlanDefinitionEntity();
        entity.setUrl(url);
        entity.setVersion(version);
        entity.setStatus(PlanDefinitionStatus.ACTIVE);
        entity.setDefinition(definitionMap);
        entity.setLoadedAt(OffsetDateTime.now());

        entity = planDefinitionRepository.save(entity);

        // Build trigger index
        buildTriggerIndex(entity, parsed);

        log.info("Loaded PlanDefinition: url={}, version={}, id={}", url, version, entity.getId());
        return entity;
    }

    /**
     * Retires a PlanDefinition by URL and version.
     *
     * @param url     the PlanDefinition canonical URL
     * @param version the PlanDefinition version
     */
    public void retirePlanDefinition(String url, String version) {
        PlanDefinitionEntity entity = planDefinitionRepository.findByUrlAndVersion(url, version)
                .orElseThrow(() -> new NoSuchElementException(
                        "PlanDefinition not found: " + url + "|" + version));

        entity.setStatus(PlanDefinitionStatus.RETIRED);
        planDefinitionRepository.save(entity);

        // Remove trigger index entries
        triggerIndexRepository.deleteByPlanDefinitionId(entity.getId());

        log.info("Retired PlanDefinition: url={}, version={}", url, version);
    }

    /**
     * Deletes a PlanDefinition by ID.
     * Fails if any protocol instances reference this definition.
     *
     * @param id the PlanDefinition UUID
     * @throws NoSuchElementException if the PlanDefinition does not exist
     * @throws IllegalStateException  if protocol instances reference this definition
     */
    public void deletePlanDefinition(UUID id) {
        PlanDefinitionEntity entity = planDefinitionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "PlanDefinition not found: " + id));

        long instanceCount = protocolInstanceRepository.countByPlanDefinitionId(id);
        if (instanceCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete PlanDefinition " + id + ": " + instanceCount
                            + " protocol instance(s) still reference it");
        }

        // Remove trigger index entries first
        triggerIndexRepository.deleteByPlanDefinitionId(id);

        // Delete the entity
        planDefinitionRepository.delete(entity);

        log.info("Deleted PlanDefinition: id={}, url={}, version={}",
                id, entity.getUrl(), entity.getVersion());
    }

    /**
     * Retrieves a PlanDefinition by URL and version.
     */
    @Transactional(readOnly = true)
    public Optional<PlanDefinitionEntity> findByUrlAndVersion(String url, String version) {
        return planDefinitionRepository.findByUrlAndVersion(url, version);
    }

    /**
     * Retrieves all active PlanDefinitions.
     */
    @Transactional(readOnly = true)
    public List<PlanDefinitionEntity> findAllActive() {
        return planDefinitionRepository.findByStatus(PlanDefinitionStatus.ACTIVE);
    }

    /**
     * Retrieves a PlanDefinition by ID.
     */
    @Transactional(readOnly = true)
    public Optional<PlanDefinitionEntity> findById(UUID id) {
        return planDefinitionRepository.findById(id);
    }

    /**
     * Retrieves all versions of a PlanDefinition by URL.
     */
    @Transactional(readOnly = true)
    public List<PlanDefinitionEntity> findByUrl(String url) {
        return planDefinitionRepository.findByUrl(url);
    }

    /**
     * Rebuilds the trigger index for a PlanDefinition from its stored FHIR definition.
     */
    public void rebuildTriggerIndex(UUID planDefinitionId) {
        PlanDefinitionEntity entity = planDefinitionRepository.findById(planDefinitionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "PlanDefinition not found: " + planDefinitionId));

        triggerIndexRepository.deleteByPlanDefinitionId(planDefinitionId);

        try {
            String json = objectMapper.writeValueAsString(entity.getDefinition());
            PlanDefinition parsed = planDefinitionParser.parse(json);
            buildTriggerIndex(entity, parsed);
            log.info("Rebuilt trigger index for PlanDefinition: id={}", planDefinitionId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to rebuild trigger index", e);
        }
    }

    /**
     * Builds trigger index entries for a PlanDefinition by extracting triggers
     * from each action and creating indexed lookup entries.
     */
    private void buildTriggerIndex(PlanDefinitionEntity entity, PlanDefinition parsed) {
        List<PlanDefinition.PlanDefinitionActionComponent> actions =
                planDefinitionParser.extractAllActions(parsed);

        for (PlanDefinition.PlanDefinitionActionComponent action : actions) {
            String actionId = action.getId();
            if (actionId == null || actionId.isBlank()) continue;

            List<TriggerDefinition> triggers = planDefinitionParser.extractTriggers(action);
            for (TriggerDefinition trigger : triggers) {
                TriggerMode mode = mapTriggerMode(trigger);
                String resourceType = planDefinitionParser.extractResourceType(trigger);

                if (resourceType == null) continue;

                List<CodeFilter> codeFilters = planDefinitionParser.extractCodeFilters(trigger);
                if (codeFilters.isEmpty()) {
                    // No code filter — index by resource type only
                    saveTriggerIndex(resourceType, null, null,
                            entity.getId(), actionId, mode);
                } else {
                    for (CodeFilter cf : codeFilters) {
                        saveTriggerIndex(resourceType, cf.system(), cf.code(),
                                entity.getId(), actionId, mode);
                    }
                }
            }
        }
    }

    private void saveTriggerIndex(String resourceType, String codeSystem, String codeValue,
                                   UUID planDefinitionId, String actionId, TriggerMode mode) {
        TriggerIndex idx = new TriggerIndex();
        idx.setResourceType(resourceType);
        idx.setCodeSystem(codeSystem != null ? codeSystem : "");
        idx.setCodeValue(codeValue != null ? codeValue : "");
        idx.setPlanDefinitionId(planDefinitionId);
        idx.setActionId(actionId);
        idx.setTriggerMode(mode);
        entityManager.persist(idx);
    }

    private TriggerMode mapTriggerMode(TriggerDefinition trigger) {
        if (trigger.getType() == TriggerDefinition.TriggerType.NAMEDEVENT) {
            return TriggerMode.NAMED_EVENT;
        }
        return TriggerMode.DATA_ADDED;
    }
}
