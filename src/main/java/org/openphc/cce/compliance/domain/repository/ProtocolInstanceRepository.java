package org.openphc.cce.compliance.domain.repository;

import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolInstanceRepository extends JpaRepository<ProtocolInstance, UUID>,
        JpaSpecificationExecutor<ProtocolInstance> {

    List<ProtocolInstance> findByPatientId(String patientId);

    List<ProtocolInstance> findByPatientIdAndStatus(String patientId, ProtocolInstanceStatus status);

    List<ProtocolInstance> findByStatus(ProtocolInstanceStatus status);

    @Query("SELECT pi FROM ProtocolInstance pi WHERE pi.patientId = :patientId AND pi.status = 'ACTIVE'")
    List<ProtocolInstance> findActiveByPatientId(@Param("patientId") String patientId);

    @Query("SELECT pi FROM ProtocolInstance pi WHERE pi.patientId = :patientId " +
            "AND pi.planDefinition.id = :planDefinitionId AND pi.status = 'ACTIVE'")
    List<ProtocolInstance> findActiveByPatientIdAndPlanDefinition(
            @Param("patientId") String patientId,
            @Param("planDefinitionId") UUID planDefinitionId);

    long countByStatus(ProtocolInstanceStatus status);

    long countByPlanDefinitionId(UUID planDefinitionId);
}
