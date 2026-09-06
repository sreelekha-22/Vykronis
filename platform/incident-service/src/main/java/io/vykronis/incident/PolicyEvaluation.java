package io.vykronis.incident;

/**
 * Result of asking the policy service whether a remediation may proceed.
 *
 * @param decision ALLOW / DENY / REQUIRE_APPROVAL (mirrors policy-service)
 * @param reason   human-readable explanation from the policy service
 */
public record PolicyEvaluation(String decision, String reason) {
}
