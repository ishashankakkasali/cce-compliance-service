package org.openphc.cce.compliance.domain.repository;

import org.openphc.cce.compliance.domain.entity.TriggerIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TriggerIndexRepository extends JpaRepository<TriggerIndex, TriggerIndex.TriggerIndexId> {

    /**
     * Tier 1 structural match — find candidates by resource type only.
     */
    List<TriggerIndex> findByResourceType(String resourceType);

    /**
     * Tier 1 structural match — find candidates by resource type and code.
     */
    @Query("SELECT ti FROM TriggerIndex ti WHERE ti.resourceType = :resourceType " +
            "AND (ti.codeSystem IS NULL OR (ti.codeSystem = :codeSystem AND ti.codeValue = :codeValue))")
    List<TriggerIndex> findByResourceTypeAndCode(
            @Param("resourceType") String resourceType,
            @Param("codeSystem") String codeSystem,
            @Param("codeValue") String codeValue);

    List<TriggerIndex> findByPlanDefinitionId(UUID planDefinitionId);

    @Modifying
    @Query("DELETE FROM TriggerIndex ti WHERE ti.planDefinitionId = :planDefinitionId")
    void deleteByPlanDefinitionId(@Param("planDefinitionId") UUID planDefinitionId);
}
