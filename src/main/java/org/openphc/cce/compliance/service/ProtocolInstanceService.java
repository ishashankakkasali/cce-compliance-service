package org.openphc.cce.compliance.service;

import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.compliance.domain.repository.ProtocolInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * @return the active ProtocolInstance (existing or newly created)
     */
    public ProtocolInstance enrollOrGetActive(String patientId, PlanDefinitionEntity planDefinition) {
        // Check for existing active instance
        List<ProtocolInstance> existingList = protocolInstanceRepository
                .findActiveByPatientIdAndPlanDefinition(patientId, planDefinition.getId());

        if (!existingList.isEmpty()) {
            log.debug("Patient {} already enrolled in protocol {}", patientId, planDefinition.getCanonical());
            return existingList.get(0);
        }

        // Create new protocol instance
        ProtocolInstance instance = new ProtocolInstance();
        instance.setPatientId(patientId);
        instance.setProtocolCanonical(planDefinition.getCanonical());
        instance.setPlanDefinition(planDefinition);
        instance.setEnrolledAt(OffsetDateTime.now());
        instance.setStatus(ProtocolInstanceStatus.ACTIVE);
        instance.setCreatedAt(OffsetDateTime.now());
        instance.setUpdatedAt(OffsetDateTime.now());

        instance = protocolInstanceRepository.save(instance);
        log.info("Enrolled patient {} in protocol {}: instanceId={}",
                patientId, planDefinition.getCanonical(), instance.getId());
        return instance;
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
}
