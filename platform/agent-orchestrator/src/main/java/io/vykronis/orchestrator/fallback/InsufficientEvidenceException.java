package io.vykronis.orchestrator.fallback;

/**
 * Thrown by the rule-based fallback when an investigation surfaces no
 * observability evidence at all. This prevents emitting an EMPTY hypothesis —
 * the schema requires non-empty {@code evidence} — and signals the caller that
 * there is nothing deterministic to say yet.
 */
public class InsufficientEvidenceException extends RuntimeException {

    public InsufficientEvidenceException(String message) {
        super(message);
    }
}
