package org.openphc.cce.compliance.web.controller;

import org.openphc.cce.compliance.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.compliance.service.ProtocolInstanceService;
import org.openphc.cce.compliance.web.dto.ProtocolInstanceDto;
import org.openphc.cce.compliance.web.mapper.DtoMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for managing Protocol Instances.
 * <p>
 * Route prefix: {@code /v1/protocol-instances}
 */
@RestController
@RequestMapping("/v1/protocol-instances")
public class ProtocolInstanceController {

    private final ProtocolInstanceService protocolInstanceService;
    private final DtoMapper dtoMapper;

    public ProtocolInstanceController(ProtocolInstanceService protocolInstanceService,
                                       DtoMapper dtoMapper) {
        this.protocolInstanceService = protocolInstanceService;
        this.dtoMapper = dtoMapper;
    }

    /**
     * GET /v1/protocol-instances — List protocol instances with optional filters.
     * <p>
     * Filterable by patient, protocol, status, facility, and plan-definition.
     * Supports pagination via {@code page}, {@code size}, and {@code sort} query parameters.
     *
     * @param patientId         filter by patient identifier (e.g. "Patient/123")
     * @param protocolCanonical filter by protocol canonical URL
     * @param status            filter by status: active, completed, withdrawn, expired
     * @param facilityId        filter by facility identifier
     * @param planDefinitionId  filter by plan-definition UUID
     * @param pageable          pagination parameters (default: page=0, size=20, sort=createdAt,desc)
     * @return paginated list of protocol instances (without nested steps/deviations for performance)
     */
    @GetMapping
    public ResponseEntity<Page<ProtocolInstanceDto>> list(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String protocolCanonical,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) UUID planDefinitionId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        ProtocolInstanceStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            statusEnum = ProtocolInstanceStatus.fromValue(status);
        }

        Page<ProtocolInstanceDto> result = protocolInstanceService
                .search(patientId, protocolCanonical, statusEnum, facilityId, planDefinitionId, pageable)
                .map(entity -> dtoMapper.toProtocolInstanceDto(entity, false));

        return ResponseEntity.ok(result);
    }

    /**
     * GET /v1/protocol-instances/{id} — Get a protocol instance by ID (with steps and deviations).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProtocolInstanceDto> getById(@PathVariable UUID id) {
        return protocolInstanceService.findById(id)
                .map(dtoMapper::toProtocolInstanceDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /v1/protocol-instances/{id}/complete — Complete a protocol instance.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(@PathVariable UUID id) {
        protocolInstanceService.completeProtocol(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /v1/protocol-instances/{id}/withdraw — Withdraw a protocol instance.
     */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Void> withdraw(@PathVariable UUID id) {
        protocolInstanceService.withdrawProtocol(id);
        return ResponseEntity.noContent().build();
    }
}
