package io.vykronis.incident;

/**
 * A remediation cannot be requested for this incident (no hypothesis, or the
 * incident is already past the point where remediation may be requested).
 * Mapped to a 409 CONFLICT.
 */
public class RemediationNotAllowedException extends RuntimeException {
    public RemediationNotAllowedException(String message) {
        super(message);
    }
}
