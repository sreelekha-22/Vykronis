package io.vykronis.orchestrator.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import com.google.genai.Client;

/**
 * Builds exactly ONE active {@link AiProvider} from {@code vykronis.ai.provider} —
 * the model for the selected provider only. Nothing is ever constructed for an
 * unselected provider, so a missing API key or an absent local Ollama can never
 * break context startup. Manual Spring AI construction (no starters): Spring AI
 * 2.0 runs its own Jackson-3 stack internally; the platform stays on Jackson 2.
 */
@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class ProviderSelectionConfig {

    private static final double INVESTIGATION_TEMPERATURE = 0.2;

    @Bean
    AiProvider activeAiProvider(AiProviderProperties properties, RestClient.Builder restClientBuilder) {
        return switch (properties.getProvider()) {
            case NONE -> new NoOpAiProvider();
            case OLLAMA -> ollama(properties, restClientBuilder);
            case GROQ -> openAiCompat("groq", properties, properties.getGroq());
            case HUGGINGFACE -> openAiCompat("huggingface", properties, properties.getHuggingface());
            case OPENROUTER -> openAiCompat("openrouter", properties, properties.getOpenrouter());
            case GEMINI -> gemini(properties);
        };
    }

    private AiProvider ollama(AiProviderProperties properties, RestClient.Builder restClientBuilder) {
        AiProviderProperties.ProviderSpec spec = properties.getOllama();
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder()
                        .baseUrl(spec.getBaseUrl())
                        .restClientBuilder(restClientBuilder)
                        .build())
                .options(OllamaChatOptions.builder()
                        .model(spec.getModel())
                        .temperature(INVESTIGATION_TEMPERATURE)
                        .build())
                .build();
        return new SpringAiProvider("ollama", true, chatModel);
    }

    private AiProvider openAiCompat(String name, AiProviderProperties properties,
                                    AiProviderProperties.ProviderSpec spec) {
        String apiKey = properties.getApiKey();
        if (apiKey.isBlank()) {
            return new SpringAiProvider(name, false, null);
        }
        ChatModel chatModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(spec.getBaseUrl())
                        .apiKey(apiKey)
                        .model(spec.getModel())
                        .temperature(INVESTIGATION_TEMPERATURE)
                        .build())
                .build();
        return new SpringAiProvider(name, true, chatModel);
    }

    private AiProvider gemini(AiProviderProperties properties) {
        String apiKey = properties.getApiKey();
        if (apiKey.isBlank()) {
            return new SpringAiProvider("gemini", false, null);
        }
        GoogleGenAiChatModel chatModel = GoogleGenAiChatModel.builder()
                .genAiClient(Client.builder().apiKey(apiKey).build())
                .options(GoogleGenAiChatOptions.builder()
                        .model(properties.getGemini().getModel())
                        .temperature(INVESTIGATION_TEMPERATURE)
                        .build())
                .build();
        return new SpringAiProvider("gemini", true, chatModel);
    }
}