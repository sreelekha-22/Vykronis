package io.vykronis.orchestrator.hypothesis;

/**
 * Raised when raw output does not validate against
 * {@code hypothesis.schema.json}. The message carries the schema violation(s)
 * so it can be fed back to a provider or logged for the fallback path.
 */
public class HypothesisValidationException extends RuntimeException {

    public HypothesisValidationException(String message) {
        super(message);
    }

    public HypothesisValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}