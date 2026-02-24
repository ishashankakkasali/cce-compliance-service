package org.openphc.cce.compliance.web.controller;

import org.openphc.cce.compliance.domain.entity.ProtocolInstance;
import org.openphc.cce.compliance.service.EventLogService;
import org.openphc.cce.compliance.service.ProtocolInstanceService;
import org.openphc.cce.compliance.service.StepInstanceService;
import org.openphc.cce.compliance.service.DeviationService;
import org.openphc.cce.compliance.web.dto.*;
import org.openphc.cce.compliance.web.mapper.DtoMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for patient-centric protocol tracking views.
 * <p>
 * Route prefix: {@code /v1/patients/{patientId}/protocol-tracking}
 */
@RestController
@RequestMapping("/v1/patients/{patientId}")
public class ProtocolTrackingController {

    private final ProtocolInstanceService protocolInstanceService;
    private final StepInstanceService stepInstanceService;
    private final DeviationService deviationService;
    private final EventLogService eventLogService;
    private final DtoMapper dtoMapper;

    public ProtocolTrackingController(ProtocolInstanceService protocolInstanceService,
                                       StepInstanceService stepInstanceService,
                                       DeviationService deviationService,
                                       EventLogService eventLogService,
                                       DtoMapper dtoMapper) {
        this.protocolInstanceService = protocolInstanceService;
        this.stepInstanceService = stepInstanceService;
        this.deviationService = deviationService;
        this.eventLogService = eventLogService;
        this.dtoMapper = dtoMapper;
    }

    /**
     * GET /v1/patients/{patientId}/protocol-tracking — List all protocol instances for a patient.
     */
    @GetMapping("/protocol-tracking")
    public ResponseEntity<List<ProtocolInstanceDto>> listProtocols(@PathVariable String patientId) {
        List<ProtocolInstance> instances = protocolInstanceService.findByPatientId(patientId);
        return ResponseEntity.ok(dtoMapper.toProtocolInstanceDtoList(instances));
    }

    /**
     * GET /v1/patients/{patientId}/protocol-tracking/active — List active protocol instances.
     */
    @GetMapping("/protocol-tracking/active")
    public ResponseEntity<List<ProtocolInstanceDto>> listActiveProtocols(@PathVariable String patientId) {
        List<ProtocolInstance> instances = protocolInstanceService.findActiveByPatientId(patientId);
        return ResponseEntity.ok(dtoMapper.toProtocolInstanceDtoList(instances));
    }

    /**
     * GET /v1/patients/{patientId}/protocol-tracking/{protocolInstanceId} — Get details with steps.
     */
    @GetMapping("/protocol-tracking/{protocolInstanceId}")
    public ResponseEntity<ProtocolInstanceDto> getProtocolDetail(
            @PathVariable String patientId,
            @PathVariable UUID protocolInstanceId) {
        return protocolInstanceService.findById(protocolInstanceId)
                .filter(p -> patientId.equals(p.getPatientId()))
                .map(dtoMapper::toProtocolInstanceDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /v1/patients/{patientId}/protocol-tracking/{protocolInstanceId}/steps — List steps.
     */
    @GetMapping("/protocol-tracking/{protocolInstanceId}/steps")
    public ResponseEntity<List<StepInstanceDto>> listSteps(
            @PathVariable String patientId,
            @PathVariable UUID protocolInstanceId) {
        List<StepInstanceDto> steps = stepInstanceService.findByProtocolInstanceId(protocolInstanceId)
                .stream()
                .map(dtoMapper::toStepInstanceDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(steps);
    }

    /**
     * GET /v1/patients/{patientId}/protocol-tracking/{protocolInstanceId}/deviations — List deviations.
     */
    @GetMapping("/protocol-tracking/{protocolInstanceId}/deviations")
    public ResponseEntity<List<DeviationDto>> listDeviations(
            @PathVariable String patientId,
            @PathVariable UUID protocolInstanceId) {
        List<DeviationDto> deviations = deviationService.findByProtocolInstanceId(protocolInstanceId)
                .stream()
                .map(dtoMapper::toDeviationDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(deviations);
    }

    /**
     * GET /v1/patients/{patientId}/events — List patient events (paginated).
     */
    @GetMapping("/events")
    public ResponseEntity<Page<EventLogDto>> listPatientEvents(
            @PathVariable String patientId,
            Pageable pageable) {
        Page<EventLogDto> events = eventLogService.findBySubject(patientId, pageable)
                .map(dtoMapper::toEventLogDto);
        return ResponseEntity.ok(events);
    }
}
