package io.vykronis.orchestrator.ai;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Config-only provider selection (Phase 4 Unit 4): the value of
 * {@code vykronis.ai.provider} decides which {@link AiProvider} is active, and
 * nothing is ever built for a provider that is not selected.
 */
class ProviderSelectionConfigTest {

    private final ProviderSelectionConfig config = new ProviderSelectionConfig();

    @Test
    void noneSelectsTheNoOpProvider() {
        AiProvider provider = select(provider(AiProviderName.NONE));

        assertThat(provider).isInstanceOf(NoOpAiProvider.class);
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void ollamaSelectsAnAvailableSpringAiProvider() {
        AiProviderProperties props = provider(AiProviderName.OLLAMA);
        props.getOllama().setBaseUrl("http://localhost:11434");
        props.getOllama().setModel("llama3.2");

        AiProvider provider = select(props);

        assertThat(provider).isInstanceOf(SpringAiProvider.class);
        SpringAiProvider springAi = (SpringAiProvider) provider;
        assertThat(springAi.name()).isEqualTo("ollama");
        assertThat(springAi.isAvailable()).isTrue();
    }

    @Test
    void openAiCompatProvidersRequireApiKey() {
        for (AiProviderName name : new AiProviderName[]{
                AiProviderName.GROQ, AiProviderName.HUGGINGFACE, AiProviderName.OPENROUTER}) {
            AiProvider unavailable = select(provider(name));
            assertThat(unavailable).isInstanceOf(SpringAiProvider.class);
            assertThat(((SpringAiProvider) unavailable).name()).isEqualTo(name.name().toLowerCase());
            assertThat(unavailable.isAvailable()).isFalse();

            AiProviderProperties withKey = provider(name);
            withKey.setApiKey("test-key");
            AiProvider available = select(withKey);
            assertThat(available.isAvailable()).isTrue();
        }
    }

    @Test
    void geminiRequiresApiKey() {
        AiProviderProperties noKey = provider(AiProviderName.GEMINI);
        assertThat(select(noKey).isAvailable()).isFalse();

        AiProviderProperties withKey = provider(AiProviderName.GEMINI);
        withKey.setApiKey("test-key");
        withKey.getGemini().setModel("gemini-2.5-flash");
        AiProvider available = select(withKey);
        assertThat(available).isInstanceOf(SpringAiProvider.class);
        assertThat(((SpringAiProvider) available).name()).isEqualTo("gemini");
        assertThat(available.isAvailable()).isTrue();
    }

    private AiProvider select(AiProviderProperties props) {
        return config.activeAiProvider(props, RestClient.builder());
    }

    private static AiProviderProperties provider(AiProviderName name) {
        AiProviderProperties props = new AiProviderProperties();
        props.setProvider(name);
        return props;
    }
}