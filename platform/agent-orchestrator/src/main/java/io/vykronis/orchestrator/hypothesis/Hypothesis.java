package io.vykronis.orchestrator.hypothesis;

import java.util.List;
import java.util.UUID;

/**
 * A validated investigation hypothesis for an incident. Instances are only
 * created from raw JSON that has passed {@link HypothesisValidator} — malformed
 * or schema-violating model output can never reach this shape.
 *
 * @param incidentId              incident this hypothesis explains
 * @param statement               the hypothesis in one sentence
 * @param summary                 optional rationale
 * @param confidence              (0, 1]
 * @param affectedServiceId       service believed at fault
 * @param affectedServiceVersion  deployment version of that service when known
 * @param source                  ai or fallback
 * @param evidence                non-empty observability evidence
 */
public record Hypothesis(
        UUID incidentId,
        String statement,
        String summary,
        double confidence,
        String affectedServiceId,
        String affectedServiceVersion,
        HypothesisSource source,
        List<HypothesisEvidence> evidence) {
}