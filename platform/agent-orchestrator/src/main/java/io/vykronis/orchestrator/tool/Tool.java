package io.vykronis.orchestrator.tool;

import java.util.Map;

/**
 * A single agent tool. The tool catalog IS the allow-list: the registry holds
 * exactly these implementations and nothing else, so an LLM can only ever
 * produce an effect that one of these tools performs.
 */
public interface Tool {

    ToolSpec spec();

    /**
     * Executes the tool. {@code arguments} are validated and coerced by the
     * implementation; the only permitted effect is an HTTP call through
     * {@link ToolHttpClient}, which enforces the endpoint/path allow-list even
     * if this implementation is buggy.
     */
    String invoke(ToolHttpClient http, Map<String, Object> arguments);
}