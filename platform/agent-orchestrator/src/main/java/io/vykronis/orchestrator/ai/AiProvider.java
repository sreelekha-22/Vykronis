package io.vykronis.orchestrator.ai;

/**
 * The single seam over the LLM. All orchestration code depends on this
 * interface — never on a concrete provider — so tests can mock the provider and
 * the rule-based fallback (Phase 4) can swap in without affecting callers.
 */
public interface AiProvider {

    /**
     * Whether this provider is configured and reachable right now.
     */
    boolean isAvailable();

    /**
     * Completes the given prompt. Throws {@link AiProviderUnavailableException}
     * when the provider cannot answer.
     */
    AiResponse complete(AiRequest request);
}