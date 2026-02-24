package org.openphc.cce.compliance.domain.repository;

import org.openphc.cce.compliance.domain.enums.StepState;
import org.openphc.cce.compliance.domain.entity.StepInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StepInstanceRepository extends JpaRepository<StepInstance, UUID> {

    List<StepInstance> findByProtocolInstanceId(UUID protocolInstanceId);

    List<StepInstance> findByProtocolInstanceIdAndState(UUID protocolInstanceId, StepState state);

    @Query("SELECT si FROM StepInstance si WHERE si.protocolInstance.id = :protocolInstanceId " +
            "AND si.actionId = :actionId AND si.state IN ('PENDING', 'DUE', 'OVERDUE')")
    List<StepInstance> findActiveByProtocolInstanceAndAction(
            @Param("protocolInstanceId") UUID protocolInstanceId,
            @Param("actionId") String actionId);

    @Query("SELECT si FROM StepInstance si WHERE si.state IN ('PENDING', 'DUE', 'OVERDUE') " +
            "AND si.dueDate <= :now")
    List<StepInstance> findStepsDueBy(@Param("now") OffsetDateTime now);

    @Query("SELECT si FROM StepInstance si WHERE si.state = 'DUE' AND si.overdueDate <= :now")
    List<StepInstance> findStepsOverdueBy(@Param("now") OffsetDateTime now);

    @Query("SELECT si FROM StepInstance si WHERE si.state = 'OVERDUE' AND si.missedDate <= :now")
    List<StepInstance> findStepsMissedBy(@Param("now") OffsetDateTime now);
}
