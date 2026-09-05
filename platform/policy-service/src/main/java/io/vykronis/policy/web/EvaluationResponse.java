package io.vykronis.policy.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /api/policy/evaluate}.
 *
 * @param incidentId    echoed from the request
 * @param action        evaluated action
 * @param environment   evaluated environment
 * @param decision      ALLOW / DENY / REQUIRE_APPROVAL
 * @param reason        human-readable explanation
 * @param auditSequence position in the append-only audit log
 * @param auditedAt     when the decision was recorded
 */
public record EvaluationResponse(
        @JsonProperty("incidentId") UUID incidentId,
        @JsonProperty("action") PolicyAction action,
        @JsonProperty("environment") Env environment,
        @JsonProperty("decision") PolicyDecision decision,
        @JsonProperty("reason") String reason,
        @JsonProperty("auditSequence") long auditSequence,
        @JsonProperty("auditedAt") Instant auditedAt) {
}