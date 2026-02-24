package org.openphc.cce.compliance.domain.repository;

import org.openphc.cce.compliance.domain.enums.PlanDefinitionStatus;
import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanDefinitionRepository extends JpaRepository<PlanDefinitionEntity, UUID> {

    Optional<PlanDefinitionEntity> findByUrlAndVersion(String url, String version);

    List<PlanDefinitionEntity> findByStatus(PlanDefinitionStatus status);

    List<PlanDefinitionEntity> findByUrl(String url);

    boolean existsByUrlAndVersion(String url, String version);
}
