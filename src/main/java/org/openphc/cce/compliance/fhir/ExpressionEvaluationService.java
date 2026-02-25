package org.openphc.cce.compliance.fhir;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tier 2 expression evaluation service for CCE compliance conditions.
 * <p>
 * Supports multiple expression languages:
 * <ul>
 *   <li>{@code text/jsonlogic} — JSONLogic expressions (CCE Solution Design Section 4.3.2.5)</li>
 *   <li>{@code text/cql} — Clinical Quality Language (CQL) expressions</li>
 *   <li>{@code text/fhirpath} — FHIR FHIRPath expressions</li>
 * </ul>
 * <p>
 * The variable binding contract:
 * <ul>
 *   <li>{@code event} — the inbound FHIR resource data (CloudEvent payload)</li>
 *   <li>{@code patient} — patient-level context</li>
 *   <li>{@code step} — current step instance context</li>
 *   <li>{@code protocol} — protocol instance context</li>
 * </ul>
 */
@Service
public class ExpressionEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ExpressionEvaluationService.class);

    private static final String LANGUAGE_JSONLOGIC = "text/jsonlogic";
    private static final String LANGUAGE_CQL = "text/cql";
    private static final String LANGUAGE_FHIRPATH = "text/fhirpath";

    private final JsonLogic jsonLogic;
    private final ObjectMapper objectMapper;
    private final CqlEvaluationEngine cqlEvaluationEngine;
    private final IFhirPath fhirPath;
    private final ca.uhn.fhir.parser.IParser fhirJsonParser;

    public ExpressionEvaluationService(ObjectMapper objectMapper,
                                        CqlEvaluationEngine cqlEvaluationEngine,
                                        IFhirPath fhirPath,
                                        ca.uhn.fhir.parser.IParser fhirJsonParser) {
        this.jsonLogic = new JsonLogic();
        this.objectMapper = objectMapper;
        this.cqlEvaluationEngine = cqlEvaluationEngine;
        this.fhirPath = fhirPath;
        this.fhirJsonParser = fhirJsonParser;
    }

    /**
     * Evaluates a condition expression against the provided variable bindings.
     *
     * @param language   the expression language (e.g., "text/jsonlogic")
     * @param expression the expression string
     * @param variables  the variable binding map
     * @return true if the condition is met, false otherwise
     * @throws UnsupportedOperationException if the language is not supported
     */
    public boolean evaluate(String language, String expression, Map<String, Object> variables) {
        if (language == null || expression == null || expression.isBlank()) {
            // No condition means the trigger matches unconditionally
            return true;
        }

        return switch (language) {
            case LANGUAGE_JSONLOGIC -> evaluateJsonLogic(expression, variables);
            case LANGUAGE_CQL -> evaluateCql(expression, variables);
            case LANGUAGE_FHIRPATH -> evaluateFhirPath(expression, variables);
            default -> {
                log.warn("Unsupported expression language: {}. Treating as unconditionally true.", language);
                yield true;
            }
        };
    }

    /**
     * Evaluates a JSONLogic expression.
     *
     * @param expression the JSONLogic JSON expression string
     * @param variables  the variable bindings
     * @return true if the expression evaluates to a truthy value
     */
    private boolean evaluateJsonLogic(String expression, Map<String, Object> variables) {
        try {
            // Parse the expression to a JsonNode for validation
            JsonNode expressionNode = objectMapper.readTree(expression);

            // Convert variables to the format expected by json-logic-java
            Object data = objectMapper.convertValue(variables, Map.class);

            Object result = jsonLogic.apply(expressionNode.toString(), data);

            boolean boolResult = isTruthy(result);
            log.debug("JSONLogic evaluation: expression={}, result={}", expression, boolResult);
            return boolResult;

        } catch (JsonLogicException ex) {
            log.error("JSONLogic evaluation failed for expression: {}", expression, ex);
            throw new ExpressionEvaluationException(
                    "JSONLogic evaluation failed: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Error parsing or evaluating JSONLogic expression: {}", expression, ex);
            throw new ExpressionEvaluationException(
                    "Expression evaluation error: " + ex.getMessage(), ex);
        }
    }

    /**
     * Evaluates a CQL (Clinical Quality Language) expression.
     *
     * @param expression the CQL expression string
     * @param variables  the variable bindings
     * @return true if the expression evaluates to a truthy value
     */
    private boolean evaluateCql(String expression, Map<String, Object> variables) {
        log.debug("Evaluating CQL expression: {}", expression);
        return cqlEvaluationEngine.evaluate(expression, variables);
    }

    /**
     * Evaluates a FHIRPath expression against the event's FHIR resource.
     * <p>
     * Parses the event data as a FHIR resource and evaluates the FHIRPath
     * expression against it. Returns true if the result is a non-empty collection
     * or a single boolean true.
     *
     * @param expression the FHIRPath expression string
     * @param variables  the variable bindings (expects 'event' or 'resource' key
     *                   containing a Map representation of a FHIR resource)
     * @return true if the expression evaluates to truthy
     */
    @SuppressWarnings("unchecked")
    private boolean evaluateFhirPath(String expression, Map<String, Object> variables) {
        try {
            // Get the FHIR resource data from variables
            Object resourceData = variables.get("resource");
            if (resourceData == null) {
                resourceData = variables.get("event");
            }
            if (resourceData == null) {
                log.warn("No FHIR resource data for FHIRPath evaluation, treating as false");
                return false;
            }

            // Convert the Map to a FHIR resource by serializing to JSON and parsing
            String resourceJson;
            if (resourceData instanceof Map) {
                resourceJson = objectMapper.writeValueAsString(resourceData);
            } else if (resourceData instanceof String s) {
                resourceJson = s;
            } else {
                log.warn("Unsupported resource data type for FHIRPath: {}", resourceData.getClass());
                return false;
            }

            // Parse as a generic FHIR resource
            org.hl7.fhir.r4.model.Resource resource =
                    (org.hl7.fhir.r4.model.Resource) fhirJsonParser.parseResource(resourceJson);

            // Evaluate the FHIRPath expression
            List<Base> results = fhirPath.evaluate(resource, expression, Base.class);

            if (results.isEmpty()) {
                return false;
            }
            // If result is a single boolean, return its value
            if (results.size() == 1 && results.get(0) instanceof BooleanType boolResult) {
                return boolResult.booleanValue();
            }
            // Non-empty result set is truthy
            boolean result = !results.isEmpty();
            log.debug("FHIRPath evaluation: expression={}, resultCount={}, result={}",
                    expression, results.size(), result);
            return result;

        } catch (Exception ex) {
            log.error("FHIRPath evaluation failed for expression: {}", expression, ex);
            throw new ExpressionEvaluationException(
                    "FHIRPath evaluation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Determines whether a JSONLogic result is truthy.
     *
     * @param result the result from JSONLogic evaluation
     * @return true if the result is truthy
     */
    private boolean isTruthy(Object result) {
        if (result == null) return false;
        if (result instanceof Boolean b) return b;
        if (result instanceof Number n) return n.doubleValue() != 0;
        if (result instanceof String s) return !s.isEmpty();
        return true;
    }

    /**
     * Builds the standard variable binding map for condition evaluation.
     *
     * @param eventData  the CloudEvent data payload
     * @param patientCtx patient-level context
     * @param stepCtx    step instance context
     * @param protocolCtx protocol instance context
     * @return the variable binding map (includes both 'event' and 'resource' keys for FHIR compatibility)
     */
    public Map<String, Object> buildVariables(Map<String, Object> eventData,
                                               Map<String, Object> patientCtx,
                                               Map<String, Object> stepCtx,
                                               Map<String, Object> protocolCtx) {
        Map<String, Object> eventDataSafe = eventData != null ? eventData : Map.of();
        Map<String, Object> variables = new HashMap<>();
        variables.put("event", eventDataSafe);
        variables.put("resource", eventDataSafe);
        variables.put("patient", patientCtx != null ? patientCtx : Map.of());
        variables.put("step", stepCtx != null ? stepCtx : Map.of());
        variables.put("protocol", protocolCtx != null ? protocolCtx : Map.of());
        return variables;
    }

    /**
     * Exception thrown when expression evaluation fails.
     */
    public static class ExpressionEvaluationException extends RuntimeException {
        public ExpressionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
