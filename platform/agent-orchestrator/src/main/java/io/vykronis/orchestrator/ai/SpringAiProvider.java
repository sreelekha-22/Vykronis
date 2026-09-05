package io.vykronis.orchestrator.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Adapter that exposes a Spring AI {@link ChatModel} through the platform's
 * {@link AiProvider} seam — the agent/investigation code never imports Spring AI
 * types. Every failure (unreachable provider, missing key, empty reply) surfaces
 * as {@link AiProviderUnavailableException} so the rule-based fallback (Unit 5)
 * only has to catch one exception type.
 *
 * @param name      provider identifier (ollama, groq, gemini, huggingface, openrouter)
 * @param available whether the provider is configured for use
 * @param chatModel Spring AI model; {@code null} when {@code available} is false
 */
public final class SpringAiProvider implements AiProvider {

    private final String name;
    private final boolean available;
    private final ChatModel chatModel;

    public SpringAiProvider(String name, boolean available, ChatModel chatModel) {
        this.name = name;
        this.available = available;
        this.chatModel = chatModel;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        if (!available) {
            throw new AiProviderUnavailableException("AI provider " + name + " is not configured");
        }
        try {
            ChatResponse response = chatModel.call(new Prompt(
                    new SystemMessage(request.systemPrompt()),
                    new UserMessage(request.userPrompt())));
            Generation result = response.getResult();
            if (result == null || result.getOutput() == null || result.getOutput().getText() == null) {
                throw new AiProviderUnavailableException("AI provider " + name + " returned no content");
            }
            return new AiResponse(name, result.getOutput().getText().trim());
        } catch (AiProviderUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderUnavailableException(
                    "AI provider " + name + " failed: " + e.getMessage(), e);
        }
    }
}