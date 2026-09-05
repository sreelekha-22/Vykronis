package io.vykronis.policy.audit;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;
import io.vykronis.policy.PolicySubject;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * One immutable, tamper-evident decision record. Each entry carries the hash
 * of the previous entry and its own SHA-256 over a canonical encoding of all
 * fields, forming an append-only chain.
 */
public record AuditEntry(
        long sequence,
        Instant decidedAt,
        PolicyAction action,
        Env environment,
        PolicySubject subject,
        PolicyDecision decision,
        String previousHash,
        String hash) {

    public static AuditEntry of(long sequence, Instant decidedAt, PolicyAction action, Env environment,
            PolicySubject subject, PolicyDecision decision, String previousHash) {
        String hash = Hashing.sha256(canonical(sequence, decidedAt, action, environment, subject, decision, previousHash));
        return new AuditEntry(sequence, decidedAt, action, environment, subject, decision, previousHash, hash);
    }

    /**
     * True when this entry directly follows {@code previous} in an unbroken,
     * untampered chain: the recorded predecessor hash matches, and this entry's
     * recorded hash equals a fresh hash of its own canonical fields.
     */
    public boolean matchesPrevious(AuditEntry previous) {
        return this.sequence == previous.sequence + 1
                && this.previousHash.equals(previous.hash)
                && this.hash.equals(Hashing.sha256(
                        canonical(sequence, decidedAt, action, environment, subject, decision, previousHash)));
    }

    /**
     * Deterministic canonical encoding. Roles are sorted so the hash is stable
     * regardless of set iteration order.
     */
    static String canonical(long sequence, Instant decidedAt, PolicyAction action, Env environment,
            PolicySubject subject, PolicyDecision decision, String previousHash) {
        String subjectPart = subject.name()
                + ";" + subject.roles().stream().sorted().collect(Collectors.joining(","))
                + ";" + subject.service();
        return sequence
                + "|" + decidedAt.toEpochMilli()
                + "|" + action
                + "|" + environment
                + "|" + subjectPart
                + "|" + decision
                + "|" + previousHash;
    }
}