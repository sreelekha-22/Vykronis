package io.vykronis.orchestrator.ai;

import org.springframework.stereotype.Service;

/**
 * The only path orchestration code uses into an LLM. Delegates to whatever
 * {@link AiProvider} is active and guarantees the availability contract:
 * nothing is sent to a provider that reports itself unavailable.
 */
@Service
public class AiGateway {

    private final AiProvider provider;

    public AiGateway(AiProvider provider) {
        this.provider = provider;
    }

    public boolean isAvailable() {
        return provider.isAvailable();
    }

    public AiResponse complete(AiRequest request) {
        if (!provider.isAvailable()) {
            throw new AiProviderUnavailableException(
                    "AI provider " + provider.getClass().getSimpleName() + " is unavailable");
        }
        return provider.complete(request);
    }
}