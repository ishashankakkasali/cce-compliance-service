package org.openphc.cce.compliance.web.controller;

import org.openphc.cce.compliance.service.ProtocolInstanceService;
import org.openphc.cce.compliance.web.dto.ProtocolInstanceDto;
import org.openphc.cce.compliance.web.mapper.DtoMapper;
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
