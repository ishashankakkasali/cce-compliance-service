package org.openphc.cce.compliance.service;

import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.compliance.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.compliance.domain.repository.ProtocolInstanceSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages protocol instance lifecycle — enrollment, completion, withdrawal, and queries.
 */
@Service
@Transactional
public class ProtocolInstanceService {

    private static final Logger log = LoggerFactory.getLogger(ProtocolInstanceService.class);

    private final ProtocolInstanceRepository protocolInstanceRepository;

    public ProtocolInstanceService(ProtocolInstanceRepository protocolInstanceRepository) {
        this.protocolInstanceRepository = protocolInstanceRepository;
    }

    /**
     * Enrolls a patient in a protocol based on a PlanDefinition.
     * If the patient already has an ACTIVE instance of this protocol, returns the existing one.
     *
     * @param patientId      the patient identifier (FHIR Patient reference)
     * @param planDefinition the PlanDefinition to enroll against
     * @param facilityId     the facility identifier from the inbound event (nullable)
     * @return the active ProtocolInstance (existing or newly created)
     */
    public ProtocolInstance enrollOrGetActive(String patientId, PlanDefinitionEntity planDefinition,
                                               String facilityId) {
        // Check for existing active instance
        List<ProtocolInstance> existingList = protocolInstanceRepository
                .findActiveByPatientIdAndPlanDefinition(patientId, planDefinition.getId());

        if (!existingList.isEmpty()) {
            ProtocolInstance existing = existingList.get(0);
            // Backfill facilityId if missing on existing instance
            if (existing.getFacilityId() == null && facilityId != null && !facilityId.isBlank()) {
                existing.setFacilityId(facilityId);
                existing.setUpdatedAt(OffsetDateTime.now());
                existing = protocolInstanceRepository.save(existing);
            }
            log.debug("Patient {} already enrolled in protocol {}", patientId, planDefinition.getCanonical());
            return existing;
        }

        // Create new protocol instance
        ProtocolInstance instance = new ProtocolInstance();
        instance.setPatientId(patientId);
        instance.setProtocolCanonical(planDefinition.getCanonical());
        instance.setPlanDefinition(planDefinition);
        instance.setFacilityId(facilityId);
        instance.setEnrolledAt(OffsetDateTime.now());
        instance.setStatus(ProtocolInstanceStatus.ACTIVE);
        instance.setCreatedAt(OffsetDateTime.now());
        instance.setUpdatedAt(OffsetDateTime.now());

        instance = protocolInstanceRepository.save(instance);
        log.info("Enrolled patient {} in protocol {}: instanceId={}, facilityId={}",
                patientId, planDefinition.getCanonical(), instance.getId(), facilityId);
        return instance;
    }

    /**
     * Enrolls a patient in a protocol (convenience overload without facilityId).
     */
    public ProtocolInstance enrollOrGetActive(String patientId, PlanDefinitionEntity planDefinition) {
        return enrollOrGetActive(patientId, planDefinition, null);
    }

    /**
     * Marks a protocol instance as completed.
     */
    public void completeProtocol(UUID protocolInstanceId) {
        ProtocolInstance instance = protocolInstanceRepository.findById(protocolInstanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Protocol instance not found: " + protocolInstanceId));
        instance.setStatus(ProtocolInstanceStatus.COMPLETED);
        instance.setUpdatedAt(OffsetDateTime.now());
        protocolInstanceRepository.save(instance);
        log.info("Completed protocol instance: {}", protocolInstanceId);
    }

    /**
     * Marks a protocol instance as withdrawn.
     */
    public void withdrawProtocol(UUID protocolInstanceId) {
        ProtocolInstance instance = protocolInstanceRepository.findById(protocolInstanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Protocol instance not found: " + protocolInstanceId));
        instance.setStatus(ProtocolInstanceStatus.WITHDRAWN);
        instance.setUpdatedAt(OffsetDateTime.now());
        protocolInstanceRepository.save(instance);
        log.info("Withdrew protocol instance: {}", protocolInstanceId);
    }

    /**
     * Finds all protocol instances for a patient.
     */
    @Transactional(readOnly = true)
    public List<ProtocolInstance> findByPatientId(String patientId) {
        return protocolInstanceRepository.findByPatientId(patientId);
    }

    /**
     * Finds all active protocol instances for a patient.
     */
    @Transactional(readOnly = true)
    public List<ProtocolInstance> findActiveByPatientId(String patientId) {
        return protocolInstanceRepository.findActiveByPatientId(patientId);
    }

    /**
     * Finds a protocol instance by ID.
     */
    @Transactional(readOnly = true)
    public Optional<ProtocolInstance> findById(UUID id) {
        return protocolInstanceRepository.findById(id);
    }

    /**
     * Finds all protocol instances with a given status.
     */
    @Transactional(readOnly = true)
    public List<ProtocolInstance> findByPatientIdAndStatus(String patientId, ProtocolInstanceStatus status) {
        return protocolInstanceRepository.findByPatientIdAndStatus(patientId, status);
    }

    /**
     * Counts protocol instances by status.
     */
    @Transactional(readOnly = true)
    public long countByStatus(ProtocolInstanceStatus status) {
        return protocolInstanceRepository.countByStatus(status);
    }

    /**
     * Searches protocol instances with optional filters (all ANDed together).
     * Supports pagination via Spring Data's {@link Pageable}.
     *
     * @param patientId          filter by patient identifier (nullable)
     * @param protocolCanonical  filter by protocol canonical URL (nullable)
     * @param status             filter by status (nullable)
     * @param facilityId         filter by facility identifier (nullable)
     * @param planDefinitionId   filter by plan-definition ID (nullable)
     * @param pageable           pagination and sorting parameters
     * @return paginated list of matching protocol instances
     */
    @Transactional(readOnly = true)
    public Page<ProtocolInstance> search(String patientId,
                                          String protocolCanonical,
                                          ProtocolInstanceStatus status,
                                          String facilityId,
                                          UUID planDefinitionId,
                                          Pageable pageable) {
        return protocolInstanceRepository.findAll(
                ProtocolInstanceSpecifications.withFilters(
                        patientId, protocolCanonical, status, facilityId, planDefinitionId),
                pageable);
    }
}
