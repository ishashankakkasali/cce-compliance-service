package org.openphc.cce.compliance.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.parser.IParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HAPI FHIR context configuration.
 * <p>
 * FhirContext is thread-safe and expensive to create, so we create it once
 * as a singleton Spring bean. We use FHIR R4 as specified in the CCE design.
 */
@Configuration
public class HapiFhirConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public IParser fhirJsonParser(FhirContext fhirContext) {
        IParser parser = fhirContext.newJsonParser();
        parser.setPrettyPrint(false);
        parser.setStripVersionsFromReferences(false);
        return parser;
    }

    @Bean
    public IFhirPath fhirPath(FhirContext fhirContext) {
        return fhirContext.newFhirPath();
    }
}
