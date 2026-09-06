package io.vykronis.incident;

/**
 * The policy service could not be reached (or refused) the remediation
 * evaluation. The incident must NOT transition into any policy-derived state
 * when this happens; the caller leaves it eligible (HYPOTHESIS_READY).
 */
public class PolicyUnavailableException extends RuntimeException {
    public PolicyUnavailableException(String message) {
        super(message);
    }

    public PolicyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
