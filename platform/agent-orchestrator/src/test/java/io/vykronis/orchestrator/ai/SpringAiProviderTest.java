package io.vykronis.orchestrator.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SpringAiProvider} is the thin adapter that exposes a Spring AI
 * {@link ChatModel} through the platform's {@link AiProvider} seam: an
 * {@link AiRequest} maps to a system+user {@link Prompt}, the model answer maps
 * back to an {@link AiResponse}, and every failure surfaces as
 * {@link AiProviderUnavailableException} so the fallback does not have to know
 * Spring AI types.
 */
class SpringAiProviderTest {

    @Test
    void delegatesPromptToChatModelAndMapsResponse() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("payment-service v1.4.2")))));

        SpringAiProvider provider = new SpringAiProvider("ollama", true, model);
        AiResponse response = provider.complete(new AiRequest("You are the Vykronis agent", "Investigate"));

        assertThat(provider.isAvailable()).isTrue();
        assertThat(provider.name()).isEqualTo("ollama");
        assertThat(response.provider()).isEqualTo("ollama");
        assertThat(response.content()).isEqualTo("payment-service v1.4.2");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
        org.mockito.Mockito.verify(model).call(captor.capture());
        List<Message> instructions = captor.getValue().getInstructions();
        assertThat(instructions).extracting(Message::getText)
                .containsExactly("You are the Vykronis agent", "Investigate");
        assertThat(instructions).anyMatch(m -> m instanceof SystemMessage);
        assertThat(instructions).anyMatch(m -> m instanceof UserMessage);
    }

    @Test
    void throwsWhenUnavailableBeforeContactingAnything() {
        SpringAiProvider provider = new SpringAiProvider("groq", false, null);

        assertThat(provider.isAvailable()).isFalse();
        assertThatThrownBy(() -> provider.complete(new AiRequest("s", "u")))
                .isInstanceOf(AiProviderUnavailableException.class);
    }

    @Test
    void wrapsModelFailureAsAiProviderUnavailable() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("connection refused"));

        SpringAiProvider provider = new SpringAiProvider("gemini", true, model);

        assertThatThrownBy(() -> provider.complete(new AiRequest("s", "u")))
                .isInstanceOf(AiProviderUnavailableException.class)
                .hasMessageContaining("gemini")
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}