package io.vykronis.orchestrator.ai;

/**
 * The {@code vykronis.ai.provider=none} provider (the default). It is never
 * "available" and never completes — a platform must stay fully functional
 * without any LLM. Created by {@link ProviderSelectionConfig} so exactly one
 * {@link AiProvider} bean exists; the rule-based fallback (Phase 4 Unit 5)
 * handles investigations in this mode.
 */
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