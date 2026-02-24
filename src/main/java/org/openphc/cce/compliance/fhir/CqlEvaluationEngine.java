package org.openphc.cce.compliance.fhir;

import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.hl7.elm.r1.VersionedIdentifier;
import org.opencds.cqf.cql.engine.execution.CqlEngine;
import org.opencds.cqf.cql.engine.execution.Environment;
import org.opencds.cqf.cql.engine.execution.EvaluationResult;
import org.opencds.cqf.cql.engine.execution.ExpressionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CQL (Clinical Quality Language) evaluation engine for Tier 2 condition evaluation.
 * <p>
 * Supports inline CQL expressions embedded in FHIR PlanDefinition action conditions.
 * Expressions are automatically wrapped in a minimal CQL library, translated to ELM,
 * and evaluated using the CQF CQL Engine.
 * <p>
 * Translated libraries are cached for performance.
 */
@Component
public class CqlEvaluationEngine {

    private static final Logger log = LoggerFactory.getLogger(CqlEvaluationEngine.class);

    private final LibraryManager libraryManager;
    private final Map<String, VersionedIdentifier> translationCache = new ConcurrentHashMap<>();

    public CqlEvaluationEngine() {
        ModelManager modelManager = new ModelManager();
        CqlCompilerOptions options = CqlCompilerOptions.defaultOptions();
        this.libraryManager = new LibraryManager(modelManager, options);
    }

    /**
     * Evaluates a CQL expression against the provided variable context.
     * <p>
     * The expression is wrapped in a CQL library defining parameters for:
     * {@code event}, {@code patient}, {@code step}, {@code protocol}.
     * These parameters are populated from the {@code variables} map.
     *
     * @param cqlExpression the CQL expression string
     * @param variables     variable bindings (event, patient, step, protocol)
     * @return true if the expression evaluates to a truthy value
     * @throws ExpressionEvaluationService.ExpressionEvaluationException on translation or evaluation errors
     */
    public boolean evaluate(String cqlExpression, Map<String, Object> variables) {
        try {
            String cqlSource = wrapExpressionInLibrary(cqlExpression);

            // Translate CQL to ELM (cached)
            VersionedIdentifier libraryId = translateCql(cqlSource, cqlExpression);

            // Create execution environment and engine
            Environment environment = new Environment(libraryManager);
            CqlEngine engine = new CqlEngine(environment);

            // Evaluate the "Result" expression with the supplied parameters
            EvaluationResult evalResult = engine.evaluate(
                    libraryId,
                    Set.of("Result"),
                    variables // parameters
            );

            ExpressionResult exprResult = evalResult.forExpression("Result");
            Object value = (exprResult != null) ? exprResult.value() : null;

            boolean result = isTruthy(value);
            log.debug("CQL evaluation: expression='{}', result={}", cqlExpression, result);
            return result;

        } catch (ExpressionEvaluationService.ExpressionEvaluationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("CQL evaluation failed for expression: '{}'", cqlExpression, ex);
            throw new ExpressionEvaluationService.ExpressionEvaluationException(
                    "CQL evaluation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Translates a CQL source string to ELM and caches the result.
     */
    private VersionedIdentifier translateCql(String cqlSource, String cacheKey) {
        return translationCache.computeIfAbsent(cacheKey, key -> {
            CqlTranslator translator = CqlTranslator.fromText(cqlSource, libraryManager);

            if (!translator.getErrors().isEmpty()) {
                String errors = translator.getErrors().stream()
                        .map(Object::toString)
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Unknown CQL translation error");
                log.error("CQL translation errors: {}", errors);
                throw new ExpressionEvaluationService.ExpressionEvaluationException(
                        "CQL translation failed: " + errors, null);
            }

            CompiledLibrary compiled = translator.getTranslatedLibrary();
            log.info("Successfully translated CQL expression to ELM: '{}'", cacheKey);
            return compiled.getIdentifier();
        });
    }

    /**
     * Wraps an inline CQL expression in a minimal CQL library.
     * <p>
     * The generated library uses FHIR R4, declares context as Unfiltered (no
     * specific patient context required), and defines parameters matching the
     * standard variable binding contract (event, patient, step, protocol).
     */
    private String wrapExpressionInLibrary(String expression) {
        return """
                library InlineExpression version '1.0'
                using FHIR version '4.0.1'

                context Unfiltered

                define "Result":
                  %s
                """.formatted(expression);
    }

    /**
     * Determines whether a CQL evaluation result is truthy.
     */
    private boolean isTruthy(Object result) {
        if (result == null) return false;
        if (result instanceof Boolean b) return b;
        if (result instanceof Number n) return n.doubleValue() != 0;
        if (result instanceof String s) return !s.isEmpty();
        return true;
    }

    /**
     * Clears the translation cache. Useful for testing or when
     * expression definitions are updated.
     */
    public void clearCache() {
        translationCache.clear();
        log.info("CQL translation cache cleared");
    }
}
