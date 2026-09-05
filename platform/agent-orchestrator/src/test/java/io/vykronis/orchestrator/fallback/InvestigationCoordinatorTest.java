package io.vykronis.orchestrator.fallback;

import io.vykronis.orchestrator.ai.AiGateway;
import io.vykronis.orchestrator.ai.AiProviderUnavailableException;
import io.vykronis.orchestrator.ai.AiRequest;
import io.vykronis.orchestrator.ai.AiResponse;
import io.vykronis.orchestrator.hypothesis.Hypothesis;
import io.vykronis.orchestrator.hypothesis.HypothesisSource;
import io.vykronis.orchestrator.hypothesis.HypothesisValidationException;
import io.vykronis.orchestrator.hypothesis.HypothesisValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InvestigationCoordinator} is the decision seam: an AI provider answer
 * wins when it is non-blank and schema-valid; otherwise (provider none/down,
 * blank output, or schema-invalid output) it falls back to the rule-based
 * investigator, which uses real tool evidence only (Phase 4 Unit 5).
 */
class InvestigationCoordinatorTest {

    private static final UUID INCIDENT = UUID.fromString("3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a");

    private final AiGateway gateway = mock(AiGateway.class);
    private final HypothesisValidator validator = mock(HypothesisValidator.class);
    private final RuleBasedInvestigator fallback = mock(RuleBasedInvestigator.class);

    private InvestigationCoordinator coordinator() {
        return new InvestigationCoordinator(gateway, validator, fallback);
    }

    private Hypothesis fallbackHypothesis() {
        return new Hypothesis(INCIDENT, "fallback statement", null, 0.5,
                "payment-service", null, HypothesisSource.FALLBACK, List.of());
    }

    @Test
    void returnsProviderHypothesisWhenItIsSchemaValid() {
        String raw = "{\"incidentId\":\"" + INCIDENT + "\"}";
        Hypothesis ai = new Hypothesis(INCIDENT, "ai statement", null, 0.9,
                "checkout-service", "2.3.1", HypothesisSource.AI, List.of());
        when(gateway.complete(any(AiRequest.class))).thenReturn(new AiResponse("ollama", raw));
        when(validator.validate(raw)).thenReturn(ai);

        Hypothesis result = coordinator().investigate(INCIDENT, new AiRequest("s", "u"));

        assertThat(result).isEqualTo(ai);
        assertThat(result.source()).isEqualTo(HypothesisSource.AI);
        verify(fallback, never()).investigate(any(UUID.class));
    }

    @Test
    void fallsBackWhenProviderIsUnavailable() {
        when(gateway.complete(any(AiRequest.class)))
                .thenThrow(new AiProviderUnavailableException("provider down"));
        when(fallback.investigate(INCIDENT)).thenReturn(fallbackHypothesis());

        Hypothesis result = coordinator().investigate(INCIDENT, new AiRequest("s", "u"));

        assertThat(result.source()).isEqualTo(HypothesisSource.FALLBACK);
        verify(fallback).investigate(INCIDENT);
    }

    @Test
    void fallsBackWhenProviderOutputIsBlank() {
        when(gateway.complete(any(AiRequest.class))).thenReturn(new AiResponse("ollama", "   "));
        when(fallback.investigate(INCIDENT)).thenReturn(fallbackHypothesis());

        Hypothesis result = coordinator().investigate(INCIDENT, new AiRequest("s", "u"));

        assertThat(result.source()).isEqualTo(HypothesisSource.FALLBACK);
        verify(fallback).investigate(INCIDENT);
    }

    @Test
    void fallsBackWhenProviderOutputFailsSchemaValidation() {
        String raw = "{\"hallucinated\":true}";
        when(gateway.complete(any(AiRequest.class))).thenReturn(new AiResponse("ollama", raw));
        when(validator.validate(raw)).thenThrow(
                new HypothesisValidationException("failed schema", new RuntimeException()));
        when(fallback.investigate(INCIDENT)).thenReturn(fallbackHypothesis());

        Hypothesis result = coordinator().investigate(INCIDENT, new AiRequest("s", "u"));

        assertThat(result.source()).isEqualTo(HypothesisSource.FALLBACK);
        verify(fallback).investigate(INCIDENT);
    }

    @Test
    void propagatesInsufficientEvidenceWhenFallbackHasNothing() {
        when(gateway.complete(any(AiRequest.class)))
                .thenThrow(new AiProviderUnavailableException("provider none"));
        when(fallback.investigate(INCIDENT))
                .thenThrow(new InsufficientEvidenceException("no evidence"));

        InvestigationCoordinator coordinator = coordinator();

        assertThatThrownBy(() -> coordinator.investigate(INCIDENT, new AiRequest("s", "u")))
                .isInstanceOf(InsufficientEvidenceException.class);
    }

    @Test
    void doesNotConsultFallbackWhenProviderAnswered() {
        String raw = "{\"incidentId\":\"" + INCIDENT + "\"}";
        Hypothesis ai = new Hypothesis(INCIDENT, "ai statement", null, 0.8,
                "payment-service", null, HypothesisSource.AI, List.of());
        when(gateway.complete(any(AiRequest.class))).thenReturn(new AiResponse("groq", raw));
        when(validator.validate(raw)).thenReturn(ai);

        coordinator().investigate(INCIDENT, new AiRequest("s", "u"));

        verify(fallback, never()).investigate(any(UUID.class));
        verify(gateway).complete(any(AiRequest.class));
    }
}
