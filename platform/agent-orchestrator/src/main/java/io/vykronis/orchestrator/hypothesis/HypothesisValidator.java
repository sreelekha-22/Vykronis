package io.vykronis.orchestrator.hypothesis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.vykronis.common.json.Json;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates raw model/provider output against {@code hypothesis.schema.json}
 * (draft 2020-12) BEFORE it is mapped to a {@link Hypothesis}. Schema
 * violations, unknown (hallucinated) properties and malformed JSON are rejected
 * with {@link HypothesisValidationException}; only schema-conform output can
 * ever become an incident hypothesis (Phase 4 Unit 3).
 */
@Service
public class HypothesisValidator {

    private static final String SCHEMA_PATH = "schema/hypothesis.schema.json";

    private final JsonSchema schema;
    private final ObjectMapper mapper;

    public HypothesisValidator() {
        this(Json.mapper());
    }

    public HypothesisValidator(ObjectMapper mapper) {
        this.mapper = mapper;
        this.schema = loadSchema(mapper);
    }

    public Hypothesis validate(String rawJson) {
        JsonNode node;
        try {
            node = mapper.readTree(rawJson);
        } catch (Exception e) {
            throw new HypothesisValidationException("Hypothesis is not valid JSON", e);
        }
        Set<ValidationMessage> violations = schema.validate(node);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("; "));
            throw new HypothesisValidationException("Hypothesis failed schema validation: " + detail);
        }
        try {
            return mapper.treeToValue(node, Hypothesis.class);
        } catch (Exception e) {
            throw new HypothesisValidationException("Hypothesis passed schema but could not be bound: " + e.getMessage(), e);
        }
    }

    private static JsonSchema loadSchema(ObjectMapper mapper) {
        try {
            JsonNode schemaNode;
            try (InputStream in = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
                schemaNode = mapper.readTree(in);
            }
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            return factory.getSchema(schemaNode);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load hypothesis JSON Schema from " + SCHEMA_PATH, e);
        }
    }
}