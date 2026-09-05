package io.vykronis.orchestrator.ai;

import java.util.Objects;

/**
 * A prompt sent to the investigation LLM provider.
 *
 * @param systemPrompt the provider's system instruction (identity + constraints)
 * @param userPrompt   the concrete question/task for this investigation
 */
public record AiRequest(String systemPrompt, String userPrompt) {

    public AiRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        Objects.requireNonNull(userPrompt, "userPrompt must not be null");
    }
}