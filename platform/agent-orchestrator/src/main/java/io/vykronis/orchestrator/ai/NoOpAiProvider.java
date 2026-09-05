package io.vykronis.orchestrator.ai;

import org.springframework.stereotype.Component;

/**
 * Default provider when {@code vykronis.ai.provider=none} (the default). It is
 * never "available" and never completes — a platform must stay fully functional
 * without any LLM. The rule-based fallback (Phase 4) handles investigations in
 * this mode.
 */
@Component
public class NoOpAiProvider implements AiProvider {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        throw new AiProviderUnavailableException("AI provider is 'none' — no LLM configured");
    }
}