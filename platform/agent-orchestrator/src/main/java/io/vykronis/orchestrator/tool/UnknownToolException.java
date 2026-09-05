package io.vykronis.orchestrator.tool;

/**
 * Raised when a decision references a tool id that is not in the registry. The
 * registry holds ONLY allow-listed tools, so ids such as {@code docker.*} or
 * {@code kubectl.*} are structurally impossible (Phase 4 Unit 2).
 */
public class UnknownToolException extends RuntimeException {

    public UnknownToolException(String toolId) {
        super("Unknown tool: " + toolId + ". Only allow-listed tools are invocable.");
    }
}