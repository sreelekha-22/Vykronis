package io.vykronis.orchestrator.fallback;

import io.vykronis.orchestrator.ai.AiGateway;
import io.vykronis.orchestrator.ai.AiProviderUnavailableException;
import io.vykronis.orchestrator.ai.AiRequest;
import io.vykronis.orchestrator.ai.AiResponse;
import io.vykronis.orchestrator.hypothesis.Hypothesis;
import io.vykronis.orchestrator.hypothesis.HypothesisValidationException;
import io.vykronis.orchestrator.hypothesis.HypothesisValidator;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The decision seam for incident investigations (Phase 4 Unit 5). An AI
 * provider answer wins when it is non-blank AND validates against
 * {@code hypothesis.schema.json}. In every other case — provider configured as
 * {@code none}, provider down, blank output, or schema-invalid output — the
 * investigation falls back to {@link RuleBasedInvestigator}, which produces a
 * hypothesis from REAL tool evidence only.
 *
 * <p>The fallback is never asked to fabricate: if there is genuinely no
 * evidence it raises {@link InsufficientEvidenceException} instead of emitting
 * an empty hypothesis.</p>
 */
@Service
public class InvestigationCoordinator {

    private final AiGateway gateway;
    private final HypothesisValidator validator;
    private final RuleBasedInvestigator fallback;

    public InvestigationCoordinator(
            AiGateway gateway,
            HypothesisValidator validator,
            RuleBasedInvestigator fallback) {
        this.gateway = gateway;
        this.validator = validator;
        this.fallback = fallback;
    }

    public Hypothesis investigate(UUID incidentId, AiRequest request) {
        try {
            AiResponse response = gateway.complete(request);
            if (response.content() != null && !response.content().isBlank()) {
                Hypothesis candidate = validator.validate(response.content());
                if (incidentId.equals(candidate.incidentId())) {
                    return candidate;
                }
                // schema-valid but for a DIFFERENT incident: same trust failure
                // as schema-invalid output — the model does not decide what it
                // was asked about (Phase 4 Unit 8 end-to-end suite).
            }
        } catch (AiProviderUnavailableException | HypothesisValidationException e) {
            // provider none/down, or raw output that failed the schema gate
        }
        return fallback.investigate(incidentId);
    }
}