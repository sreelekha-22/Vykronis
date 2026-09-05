package io.vykronis.orchestrator.ai;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-context proof of config-only provider selection (Phase 4 Unit 4): the
 * value of {@code vykronis.ai.provider} decides the single active
 * {@link AiProvider} bean, properties bind with their defaults, and a context
 * always boots without an API key present.
 */
class ProviderSelectionContextTest {

    @Nested
    @SpringBootTest(properties = "vykronis.ai.provider=ollama")
    class OllamaContext {

        @Autowired
        private AiProvider activeProvider;

        @Autowired
        private AiGateway gateway;

        @Autowired
        private AiProviderProperties properties;

        @Test
        void selectsTheOllamaProviderFromConfiguration() {
            assertThat(activeProvider).isInstanceOf(SpringAiProvider.class);
            assertThat(((SpringAiProvider) activeProvider).name()).isEqualTo("ollama");
            assertThat(activeProvider.isAvailable()).isTrue();
            assertThat(gateway.isAvailable()).isTrue();

            assertThat(properties.getOllama().getBaseUrl()).isEqualTo("http://localhost:11434");
            assertThat(properties.getOllama().getModel()).isNotBlank();
        }
    }

    @Nested
    @SpringBootTest(properties = "vykronis.ai.provider=groq")
    class GroqWithoutApiKeyContext {

        @Autowired
        private AiProvider activeProvider;

        @Autowired
        private AiGateway gateway;

        @Test
        void bootsWithoutApiKeyAndReportsUnavailable() {
            assertThat(activeProvider).isInstanceOf(SpringAiProvider.class);
            assertThat(((SpringAiProvider) activeProvider).name()).isEqualTo("groq");
            assertThat(activeProvider.isAvailable()).isFalse();
            assertThat(gateway.isAvailable()).isFalse();
        }
    }
}