package io.vykronis.incident;

/**
 * Raised when an investigation targets an incident that does not exist.
 */
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(String message) {
        super(message);
    }
}