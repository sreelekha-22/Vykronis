package io.vykronis.orchestrator.hypothesis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The model's raw output is UNTRUSTED until it passes the hypothesis JSON
 * Schema ({@code hypothesis.schema.json}, draft 2020-12). A structured result
 * that fails the schema — missing fields, out-of-range confidence, hallucinated
 * unknown properties, non-object root — must be rejected outright and must
 * never become an incident hypothesis (Phase 4 Unit 3).
 */
class HypothesisValidatorTest {

    private static final String INCIDENT_UUID = "3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a";

    private final HypothesisValidator validator = new HypothesisValidator();

    @Test
    void acceptsWellFormedAiHypothesis() {
        Hypothesis hypothesis = validator.validate(minimal());

        assertThat(hypothesis.incidentId().toString()).isEqualTo(INCIDENT_UUID);
        assertThat(hypothesis.statement()).contains("v1.4.2");
        assertThat(hypothesis.confidence()).isEqualTo(0.82);
        assertThat(hypothesis.affectedServiceId()).isEqualTo("payment-service");
        assertThat(hypothesis.affectedServiceVersion()).isEqualTo("v1.4.2");
        assertThat(hypothesis.source()).isEqualTo(HypothesisSource.AI);
        assertThat(hypothesis.evidence()).hasSize(1);
        assertThat(hypothesis.evidence().getFirst().type()).isEqualTo(EvidenceType.TRACE);
        assertThat(hypothesis.evidence().getFirst().eventId()).isEqualTo("ev-1");
    }

    @Test
    void acceptsFallbackHypothesisWithoutOptionalFields() {
        Hypothesis hypothesis = validator.validate(minimal()
                .replace("\"source\": \"ai\"", "\"source\": \"fallback\"")
                .replace("\"affectedServiceVersion\": \"v1.4.2\",\n", "")
                .replace("\"summary\": \"First slow TRACE appears immediately after the v1.4.2 rollout.\",\n", "")
                .replace(", \"summary\": \"slow database call\"", ""));

        assertThat(hypothesis.source()).isEqualTo(HypothesisSource.FALLBACK);
        assertThat(hypothesis.affectedServiceVersion()).isNull();
        assertThat(hypothesis.summary()).isNull();
        assertThat(hypothesis.evidence().getFirst().summary()).isNull();
    }

    @Test
    void rejectsMissingRequiredStatement() {
        assertThatThrownBy(() -> validator.validate(minimal()
                .replace("\"statement\": \"payment-service v1.4.2 introduced the degraded-payment path\",\n", "")))
                .isInstanceOf(HypothesisValidationException.class)
                .hasMessageContaining("statement");
    }

    @Test
    void rejectsConfidenceAboveOne() {
        assertThatThrownBy(() -> validator.validate(confidence(1.5)))
                .isInstanceOf(HypothesisValidationException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void rejectsNonPositiveConfidence() {
        assertThatThrownBy(() -> validator.validate(confidence(0.0)))
                .isInstanceOf(HypothesisValidationException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void rejectsEmptyEvidenceArray() {
        assertThatThrownBy(() -> validator.validate(evidence("[]")))
                .isInstanceOf(HypothesisValidationException.class)
                .hasMessageContaining("evidence");
    }

    @Test
    void rejectsEvidenceItemWithoutEventId() {
        assertThatThrownBy(() -> validator.validate(
                evidence("[ { \"type\": \"TRACE\", \"source\": \"payment-service\" } ]")))
                .isInstanceOf(HypothesisValidationException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsUnknownEvidenceType() {
        Throwable thrown = catchThrowable(() -> validator.validate(
                evidence("[ { \"eventId\": \"ev-1\", \"type\": \"SEVERITY\", \"source\": \"payment-service\" } ]")));

        assertThat(thrown).isInstanceOf(HypothesisValidationException.class);
        assertThat(thrown.getMessage()).isNotNull();
        assertThat(thrown.getMessage().toLowerCase()).containsAnyOf("severity", "enum");
    }

    @Test
    void rejectsUnknownPropertiesAsHallucinatedFields() {
        assertThatThrownBy(() -> validator.validate(
                minimal().replace("\n}", ",\n  \"deployImmediately\": true\n}")))
                .isInstanceOf(HypothesisValidationException.class)
                .hasMessageContaining("deployImmediately");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> validator.validate("{ not json at all"))
                .isInstanceOf(HypothesisValidationException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void rejectsNonObjectRoot() {
        Throwable thrown = catchThrowable(() -> validator.validate("[1,2,3]"));

        assertThat(thrown).isInstanceOf(HypothesisValidationException.class);
        assertThat(thrown.getMessage()).containsIgnoringCase("object");
    }

    @Test
    void rejectsInvalidIncidentIdentifier() {
        Throwable thrown = catchThrowable(() -> validator.validate(minimal().replace(INCIDENT_UUID, "not-a-uuid")));

        assertThat(thrown).isInstanceOf(HypothesisValidationException.class);
        assertThat(thrown.getMessage()).isNotNull();
        assertThat(thrown.getMessage().toLowerCase()).containsAnyOf("uuid", "incidentid");
    }

    private String minimal() {
        return """
                {
                  "incidentId": "%s",
                  "statement": "payment-service v1.4.2 introduced the degraded-payment path",
                  "summary": "First slow TRACE appears immediately after the v1.4.2 rollout.",
                  "confidence": 0.82,
                  "affectedServiceId": "payment-service",
                  "affectedServiceVersion": "v1.4.2",
                  "source": "ai",
                  "evidence": [ { "eventId": "ev-1", "type": "TRACE", "source": "payment-service", "summary": "slow database call" } ]
                }
                """.formatted(INCIDENT_UUID);
    }

    private String confidence(double value) {
        return minimal().replace("\"confidence\": 0.82", "\"confidence\": " + value);
    }

    private String evidence(String arrayJson) {
        return minimal().replace(
                "[ { \"eventId\": \"ev-1\", \"type\": \"TRACE\", \"source\": \"payment-service\", \"summary\": \"slow database call\" } ]",
                arrayJson);
    }
}