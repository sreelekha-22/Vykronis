package io.vykronis.orchestrator.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Config-only provider selection (Phase 4 Unit 4). Every value is externalizable
 * via {@code VYKRONIS_*} env vars; the API key is loaded from
 * {@code VYKRONIS_AI_API_KEY} and never committed. An empty key means the hosted
 * provider is reported unavailable (the fallback takes over in Unit 5).
 */
@ConfigurationProperties(prefix = "vykronis.ai")
public class AiProviderProperties {

    private AiProviderName provider = AiProviderName.NONE;
    private String apiKey = "";
    private final ProviderSpec ollama = new ProviderSpec();
    private final ProviderSpec groq = new ProviderSpec();
    private final ProviderSpec huggingface = new ProviderSpec();
    private final ProviderSpec openrouter = new ProviderSpec();
    private final ProviderSpec gemini = new ProviderSpec();

    public AiProviderName getProvider() {
        return provider;
    }

    public void setProvider(AiProviderName provider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = Objects.requireNonNullElse(apiKey, "");
    }

    public ProviderSpec getOllama() {
        return ollama;
    }

    public ProviderSpec getGroq() {
        return groq;
    }

    public ProviderSpec getHuggingface() {
        return huggingface;
    }

    public ProviderSpec getOpenrouter() {
        return openrouter;
    }

    public ProviderSpec getGemini() {
        return gemini;
    }

    /**
     * Location of one chat provider endpoint. {@code baseUrl} is ignored for
     * Gemini (the Google GenAI SDK uses its hosted endpoint by default).
     */
    public static final class ProviderSpec {

        private String baseUrl = "";
        private String model = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNullElse(baseUrl, "");
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = Objects.requireNonNullElse(model, "");
        }
    }
}