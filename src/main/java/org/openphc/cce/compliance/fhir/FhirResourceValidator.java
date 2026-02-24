package org.openphc.cce.compliance.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates FHIR R4 resources against the base specification.
 */
@Component
public class FhirResourceValidator {

    private static final Logger log = LoggerFactory.getLogger(FhirResourceValidator.class);

    private final FhirValidator validator;

    public FhirResourceValidator(FhirContext fhirContext) {
        this.validator = fhirContext.newValidator();
    }

    /**
     * Validates a FHIR resource and returns the validation result.
     *
     * @param resource the FHIR resource to validate
     * @return the validation result
     */
    public ValidationResult validate(Resource resource) {
        return validator.validateWithResult(resource);
    }

    /**
     * Validates a FHIR resource and throws if there are any errors.
     *
     * @param resource    the FHIR resource to validate
     * @param description a description for logging purposes
     * @throws FhirValidationException if validation fails with errors
     */
    public void validateOrThrow(Resource resource, String description) {
        ValidationResult result = validator.validateWithResult(resource);
        if (!result.isSuccessful()) {
            List<String> errors = result.getMessages().stream()
                    .filter(m -> m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.ERROR
                            || m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.FATAL)
                    .map(SingleValidationMessage::getMessage)
                    .collect(Collectors.toList());

            log.warn("FHIR validation failed for {}: {}", description, errors);
            throw new FhirValidationException(description, errors);
        }

        // Log warnings
        List<String> warnings = result.getMessages().stream()
                .filter(m -> m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.WARNING)
                .map(SingleValidationMessage::getMessage)
                .toList();
        if (!warnings.isEmpty()) {
            log.info("FHIR validation warnings for {}: {}", description, warnings);
        }
    }

    /**
     * Exception thrown when FHIR resource validation fails.
     */
    public static class FhirValidationException extends RuntimeException {
        private final List<String> errors;

        public FhirValidationException(String description, List<String> errors) {
            super("FHIR validation failed for " + description + ": " + String.join("; ", errors));
            this.errors = errors;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
