package org.openphc.cce.compliance.service;

import org.openphc.cce.compliance.domain.entity.Deviation;
import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.domain.entity.StepInstance;
import org.openphc.cce.compliance.domain.enums.DeviationType;
import org.openphc.cce.compliance.domain.repository.DeviationRepository;
import org.openphc.cce.compliance.kafka.model.IntelligenceTriggerEvent;
import org.openphc.cce.compliance.kafka.producer.IntelligenceTriggerProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages deviation detection, recording, and intelligence trigger publication.
 * <p>
 * Deviations are recorded when steps transition to OVERDUE, MISSED, or
 * when ambiguous matching occurs.
 */
@Service
@Transactional
public class DeviationService {

    private static final Logger log = LoggerFactory.getLogger(DeviationService.class);

    private final DeviationRepository deviationRepository;
    private final IntelligenceTriggerProducer intelligenceTriggerProducer;

    public DeviationService(DeviationRepository deviationRepository,
                             IntelligenceTriggerProducer intelligenceTriggerProducer) {
        this.deviationRepository = deviationRepository;
        this.intelligenceTriggerProducer = intelligenceTriggerProducer;
    }

    /**
     * Records a deviation and publishes an intelligence trigger event.
     *
     * @param protocolInstance the protocol instance
     * @param stepInstance     the step instance that deviated
     * @param deviationType    the type of deviation
     * @param metadata         additional metadata for the deviation
     * @return the recorded deviation
     */
    public Deviation recordDeviation(ProtocolInstance protocolInstance,
                                      StepInstance stepInstance,
                                      DeviationType deviationType,
                                      Map<String, Object> metadata) {
        Deviation deviation = new Deviation();
        deviation.setProtocolInstance(protocolInstance);
        deviation.setStepInstance(stepInstance);
        deviation.setDeviationType(deviationType);
        deviation.setDetectedAt(OffsetDateTime.now());
        deviation.setMetadata(metadata);

        deviation = deviationRepository.save(deviation);
        log.info("Recorded deviation: id={}, type={}, stepId={}, protocolId={}",
                deviation.getId(), deviationType, stepInstance.getId(), protocolInstance.getId());

        // Publish intelligence trigger
        publishIntelligenceTrigger(deviation, protocolInstance, stepInstance);

        return deviation;
    }

    /**
     * Publishes an intelligence trigger event for a deviation.
     */
    private void publishIntelligenceTrigger(Deviation deviation,
                                             ProtocolInstance protocolInstance,
                                             StepInstance stepInstance) {
        IntelligenceTriggerEvent trigger = new IntelligenceTriggerEvent();
        trigger.setType("cce.compliance.deviation." + deviation.getDeviationType().getValue().toLowerCase());
        trigger.setSubject(protocolInstance.getPatientId());
        trigger.setProtocolInstanceId(protocolInstance.getId());
        trigger.setStepInstanceId(stepInstance.getId());
        trigger.setDeviationId(deviation.getId());
        trigger.setDeviationType(deviation.getDeviationType().getValue());
        trigger.setStepState(stepInstance.getState().getValue());
        trigger.setActionId(stepInstance.getActionId());
        trigger.setProtocolCanonical(protocolInstance.getProtocolCanonical());
        trigger.setDetectedAt(deviation.getDetectedAt());
        trigger.setMetadata(deviation.getMetadata());

        intelligenceTriggerProducer.publishTrigger(trigger);
    }

    @Transactional(readOnly = true)
    public List<Deviation> findByProtocolInstanceId(UUID protocolInstanceId) {
        return deviationRepository.findByProtocolInstanceId(protocolInstanceId);
    }

    @Transactional(readOnly = true)
    public List<Deviation> findByDeviationType(DeviationType type) {
        return deviationRepository.findByDeviationType(type);
    }

    @Transactional(readOnly = true)
    public List<Deviation> findByStepInstanceId(UUID stepInstanceId) {
        return deviationRepository.findByStepInstanceId(stepInstanceId);
    }

    @Transactional(readOnly = true)
    public long countByProtocolInstanceId(UUID protocolInstanceId) {
        return deviationRepository.countByProtocolInstanceId(protocolInstanceId);
    }
}
