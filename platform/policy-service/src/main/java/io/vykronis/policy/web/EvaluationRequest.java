package io.vykronis.policy.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicySubject;

import java.util.UUID;

/**
 * Request body for {@code POST /api/policy/evaluate}.
 *
 * @param incidentId  the incident the remediation would target (audit context)
 * @param action      remediation action to evaluate
 * @param environment DEV / PROD the action would run in
 * @param subject     the caller being evaluated
 */
public record EvaluationRequest(
        @JsonProperty("incidentId") UUID incidentId,
        @JsonProperty("action") PolicyAction action,
        @JsonProperty("environment") Env environment,
        @JsonProperty("subject") PolicySubject subject) {
}