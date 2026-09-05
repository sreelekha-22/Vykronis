package io.vykronis.incident;

/**
 * The orchestrator could not produce a hypothesis (unreachable or non-2xx).
 * The incident aggregation reverts to its previous state — an incident only
 * ever reports {@code HYPOTHESIS_READY} when a hypothesis was actually stored.
 */
public class InvestigationUnavailableException extends RuntimeException {

    public InvestigationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvestigationUnavailableException(String message) {
        super(message);
    }
}