package org.openphc.cce.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CCE Compliance Service — main application entry point.
 * <p>
 * Part of the Connected Care Engine (CCE) Compliance sub-system.
 * Responsible for:
 * <ul>
 *   <li>Managing FHIR PlanDefinition-based clinical protocols</li>
 *   <li>Tracking patient protocol adherence via step instances</li>
 *   <li>Processing inbound clinical events for compliance matching</li>
 *   <li>Detecting deviations and publishing intelligence triggers</li>
 * </ul>
 */
@SpringBootApplication
public class ComplianceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceServiceApplication.class, args);
    }
}
