package org.openphc.cce.compliance.domain.repository;

import jakarta.persistence.criteria.Predicate;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specifications for dynamic filtering of {@link ProtocolInstance} queries.
 * <p>
 * Supports filtering by patient ID, protocol canonical, status, facility ID,
 * and plan-definition ID — all optional, combined with AND logic.
 */
public final class ProtocolInstanceSpecifications {

    private ProtocolInstanceSpecifications() {
        // utility class
    }

    /**
     * Builds a dynamic {@link Specification} based on optional filter parameters.
     *
     * @param patientId          filter by patient identifier (exact match)
     * @param protocolCanonical  filter by protocol canonical URL (exact match)
     * @param status             filter by protocol instance status
     * @param facilityId         filter by facility identifier (exact match)
     * @param planDefinitionId   filter by plan-definition ID (FK)
     * @return combined specification (all non-null filters ANDed together)
     */
    public static Specification<ProtocolInstance> withFilters(
            String patientId,
            String protocolCanonical,
            ProtocolInstanceStatus status,
            String facilityId,
            UUID planDefinitionId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (patientId != null && !patientId.isBlank()) {
                predicates.add(cb.equal(root.get("patientId"), patientId));
            }
            if (protocolCanonical != null && !protocolCanonical.isBlank()) {
                predicates.add(cb.equal(root.get("protocolCanonical"), protocolCanonical));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (facilityId != null && !facilityId.isBlank()) {
                predicates.add(cb.equal(root.get("facilityId"), facilityId));
            }
            if (planDefinitionId != null) {
                predicates.add(cb.equal(root.get("planDefinition").get("id"), planDefinitionId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
