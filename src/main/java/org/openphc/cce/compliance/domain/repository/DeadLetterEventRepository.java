package org.openphc.cce.compliance.domain.repository;

import org.openphc.cce.compliance.domain.entity.DeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {

    List<DeadLetterEvent> findByResolvedFalse();

    @Query("SELECT dle FROM DeadLetterEvent dle WHERE dle.resolved = false " +
            "AND dle.nextRetryAt <= :now ORDER BY dle.nextRetryAt ASC")
    List<DeadLetterEvent> findRetryableEvents(@Param("now") OffsetDateTime now);

    long countByResolvedFalse();
}
