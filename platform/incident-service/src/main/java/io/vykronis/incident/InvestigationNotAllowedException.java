package io.vykronis.incident;

/**
 * Raised when an investigation is not permitted because the incident is not in
 * an investigable state (only OPEN and HYPOTHESIS_READY can be investigated).
 */
public class InvestigationNotAllowedException extends RuntimeException {

    public InvestigationNotAllowedException(String message) {
        super(message);
    }
}