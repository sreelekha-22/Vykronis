package io.vykronis.orchestrator.ai;

/**
 * Thrown when the configured ({@code vykronis.ai.provider}) provider is
 * {@code none} or is down. Callers that have a fallback strategy (Phase 4)
 * catch this and answer with rules instead of failing the investigation.
 */
public class AiProviderUnavailableException extends RuntimeException {

    public AiProviderUnavailableException(String message) {
        super(message);
    }
}