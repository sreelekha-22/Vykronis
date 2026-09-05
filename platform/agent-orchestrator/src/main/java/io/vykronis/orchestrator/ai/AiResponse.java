package io.vykronis.orchestrator.ai;

import java.util.Objects;

/**
 * The result of an {@link AiProvider} completion.
 *
 * @param provider the provider that produced the answer (for traces/UI)
 * @param content  the generated text
 */
public record AiResponse(String provider, String content) {

    public AiResponse {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}