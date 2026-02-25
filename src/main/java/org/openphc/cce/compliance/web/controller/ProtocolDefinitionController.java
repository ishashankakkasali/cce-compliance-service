package org.openphc.cce.compliance.web.controller;

import jakarta.validation.Valid;
import org.openphc.cce.compliance.domain.entity.PlanDefinitionEntity;
import org.openphc.cce.compliance.service.ProtocolDefinitionService;
import org.openphc.cce.compliance.web.dto.LoadPlanDefinitionRequest;
import org.openphc.cce.compliance.web.dto.PlanDefinitionDto;
import org.openphc.cce.compliance.web.mapper.DtoMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing Protocol Definitions (FHIR PlanDefinitions).
 * <p>
 * Route prefix: {@code /v1/protocol-definitions}
 */
@RestController
@RequestMapping("/v1/protocol-definitions")
public class ProtocolDefinitionController {

    private final ProtocolDefinitionService protocolDefinitionService;
    private final DtoMapper dtoMapper;

    public ProtocolDefinitionController(ProtocolDefinitionService protocolDefinitionService,
                                         DtoMapper dtoMapper) {
        this.protocolDefinitionService = protocolDefinitionService;
        this.dtoMapper = dtoMapper;
    }

    /**
     * POST /v1/protocol-definitions — Load a new PlanDefinition.
     */
    @PostMapping
    public ResponseEntity<PlanDefinitionDto> loadPlanDefinition(
            @Valid @RequestBody LoadPlanDefinitionRequest request) {
        PlanDefinitionEntity entity = protocolDefinitionService
                .loadPlanDefinition(request.planDefinitionJson());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dtoMapper.toPlanDefinitionDto(entity));
    }

    /**
     * GET /v1/protocol-definitions — List all active PlanDefinitions.
     */
    @GetMapping
    public ResponseEntity<List<PlanDefinitionDto>> listActive() {
        List<PlanDefinitionEntity> active = protocolDefinitionService.findAllActive();
        return ResponseEntity.ok(dtoMapper.toPlanDefinitionDtoList(active));
    }

    /**
     * GET /v1/protocol-definitions/{id} — Get a PlanDefinition by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlanDefinitionDto> getById(@PathVariable UUID id) {
        return protocolDefinitionService.findById(id)
                .map(dtoMapper::toPlanDefinitionDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /v1/protocol-definitions/by-url?url={url} — Get all versions by URL.
     */
    @GetMapping("/by-url")
    public ResponseEntity<List<PlanDefinitionDto>> getByUrl(@RequestParam String url) {
        List<PlanDefinitionEntity> entities = protocolDefinitionService.findByUrl(url);
        return ResponseEntity.ok(dtoMapper.toPlanDefinitionDtoList(entities));
    }

    /**
     * GET /v1/protocol-definitions/by-url-version?url={url}&version={version} — Get by URL and version.
     */
    @GetMapping("/by-url-version")
    public ResponseEntity<PlanDefinitionDto> getByUrlAndVersion(
            @RequestParam String url, @RequestParam String version) {
        return protocolDefinitionService.findByUrlAndVersion(url, version)
                .map(dtoMapper::toPlanDefinitionDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /v1/protocol-definitions/{id}/retire — Retire a PlanDefinition.
     */
    @PostMapping("/{id}/retire")
    public ResponseEntity<Void> retire(@PathVariable UUID id) {
        PlanDefinitionEntity entity = protocolDefinitionService.findById(id)
                .orElse(null);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        protocolDefinitionService.retirePlanDefinition(entity.getUrl(), entity.getVersion());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /v1/protocol-definitions/{id}/rebuild-index — Rebuild the trigger index.
     */
    @PostMapping("/{id}/rebuild-index")
    public ResponseEntity<Void> rebuildIndex(@PathVariable UUID id) {
        protocolDefinitionService.rebuildTriggerIndex(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /v1/protocol-definitions/{id} — Delete a PlanDefinition.
     * Returns 409 Conflict if protocol instances still reference it.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        protocolDefinitionService.deletePlanDefinition(id);
        return ResponseEntity.noContent().build();
    }
}
