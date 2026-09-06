package io.vykronis.incident;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Outbound client to the policy service's {@code POST /api/policy/evaluate}.
 * The body carries the remediation action, the incident env, and the acting
 * subject; the {@code decision} field (ALLOW / DENY / REQUIRE_APPROVAL) drives
 * the incident's approval state machine. Any non-2xx response surfaces as
 * {@link PolicyUnavailableException} so the incident never transitions into a
 * policy-derived state without an authoritative answer.
 */
@Component
public class PolicyClient {

    public static final String ACTION_ROLLBACK = "ROLLBACK";

    private final RestClient client;

    public PolicyClient(RestClient.Builder builder,
                        @Value("${vykronis.policy.url:http://localhost:8086}") String policyUrl) {
        this.client = builder.baseUrl(policyUrl).build();
    }

    public PolicyEvaluation evaluate(String incidentId, String action, String environment, OperatorActor subject) {
        try {
            Map<String, Object> body = Map.of(
                    "incidentId", incidentId,
                    "action", action,
                    "environment", environment,
                    "subject", Map.of(
                            "name", subject.name(),
                            "roles", subject.roles(),
                            "service", subject.service()));
            Map<?, ?> response = client.post()
                    .uri("/api/policy/evaluate")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String decision = response != null ? String.valueOf(response.get("decision")) : null;
            String reason = response != null ? String.valueOf(response.get("reason")) : null;
            return new PolicyEvaluation(decision, reason);
        } catch (RuntimeException e) {
            throw new PolicyUnavailableException(
                    "Policy evaluation failed for incident " + incidentId, e);
        }
    }
}
