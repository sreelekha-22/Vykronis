package io.vykronis.orchestrator.tool;

/**
 * Raised when a tool attempts an effect that is NOT allow-listed: either an
 * unknown endpoint service name or a request path outside that endpoint's
 * allow-list. Raised client-side, BEFORE any HTTP connection is made.
 */
public class ToolEndpointViolationException extends RuntimeException {

    public ToolEndpointViolationException(String message) {
        super(message);
    }
}