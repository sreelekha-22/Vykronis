package io.vykronis.policy;

import io.vykronis.policy.audit.AuditEntry;

import java.time.Instant;

/**
 * Outcome of a {@link PolicyService#evaluate} call: the decision, a
 * human-readable reason, and the recorded audit entry.
 *
 * @param decision  ALLOW / DENY / REQUIRE_APPROVAL
 * @param reason    why the decision was made
 * @param auditEntry the append-only record that was written
 */
public record EvaluationResult(PolicyDecision decision, String reason, AuditEntry auditEntry) {

    public Instant decidedAt() {
        return auditEntry.decidedAt();
    }

    public long auditSequence() {
        return auditEntry.sequence();
    }
}