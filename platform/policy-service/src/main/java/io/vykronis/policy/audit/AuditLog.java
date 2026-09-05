package io.vykronis.policy.audit;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;
import io.vykronis.policy.PolicySubject;

import java.util.List;

/**
 * Append-only store of every policy decision (sequence + hash-chained,
 * never mutated or deleted). The in-memory implementation satisfies Unit 3;
 * a persistent backing store can implement this interface later (Phase 6).
 */
public interface AuditLog {

    AuditEntry append(PolicyAction action, Env environment, PolicySubject subject, PolicyDecision decision);

    /** Immutable snapshot of all entries, oldest first. */
    List<AuditEntry> entries();
}