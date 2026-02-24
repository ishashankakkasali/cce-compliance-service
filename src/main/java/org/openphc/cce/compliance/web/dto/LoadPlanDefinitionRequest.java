package org.openphc.cce.compliance.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for loading a PlanDefinition.
 */
public record LoadPlanDefinitionRequest(
        @NotBlank(message = "PlanDefinition JSON is required")
        String planDefinitionJson
) {
}
