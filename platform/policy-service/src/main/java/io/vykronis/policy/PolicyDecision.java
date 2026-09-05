package io.vykronis.policy;

/**
 * Outcome of a policy evaluation.
 *
 * <p>{@code REQUIRE_APPROVAL} is the "click-to-approve" gate for PROD
 * remediations; {@code ALLOW} is the automatic path the dev loop uses.
 */
public enum PolicyDecision {
    ALLOW,
    DENY,
    REQUIRE_APPROVAL
}