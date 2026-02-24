package org.openphc.cce.compliance.domain.repository;

import org.openphc.cce.compliance.domain.entity.EventLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, UUID> {

    Optional<EventLog> findByCloudeventsIdAndSource(String cloudeventsId, String source);

    Optional<EventLog> findBySourceAndSourceEventId(String source, String sourceEventId);

    boolean existsByCloudeventsIdAndSource(String cloudeventsId, String source);

    List<EventLog> findBySubject(String subject);

    Page<EventLog> findBySubject(String subject, Pageable pageable);

    Page<EventLog> findByFacilityId(String facilityId, Pageable pageable);
}
