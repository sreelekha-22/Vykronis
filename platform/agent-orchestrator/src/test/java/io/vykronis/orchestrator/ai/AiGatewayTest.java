package io.vykronis.orchestrator.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The LLM is behind an {@link AiProvider} interface so every provider and the
 * rule-based fallback can be tested with a mock. {@link AiGateway} is the single
 * seam the agent/investigation code talks to — it never imports a concrete
 * provider implementation.
 */
class AiGatewayTest {

    @Test
    void delegatesCompletionToTheConfiguredProviderWhenAvailable() {
        AiProvider provider = mock(AiProvider.class);
        when(provider.isAvailable()).thenReturn(true);
        AiResponse expected = new AiResponse("mock", "payment-service v1.4.2; evidence: 12 refs");
        when(provider.complete(any(AiRequest.class))).thenReturn(expected);

        AiGateway gateway = new AiGateway(provider);
        AiRequest request = new AiRequest("You are the Vykronis investigation agent", "Summarize evidence");

        assertThat(gateway.complete(request)).isEqualTo(expected);
        verify(provider).complete(request);
        assertThat(gateway.isAvailable()).isTrue();
    }

    @Test
    void refusesCompletionWhenTheConfiguredProviderIsUnavailable() {
        AiProvider provider = mock(AiProvider.class);
        when(provider.isAvailable()).thenReturn(false);

        AiGateway gateway = new AiGateway(provider);

        assertThat(gateway.isAvailable()).isFalse();
        assertThatThrownBy(() -> gateway.complete(new AiRequest("s", "u")))
                .isInstanceOf(AiProviderUnavailableException.class);
    }

    @Test
    void rejectsRequestsMissingRequiredFields() {
        AiGateway gateway = new AiGateway(mock(AiProvider.class));

        assertThatThrownBy(() -> new AiRequest(null, "u"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AiRequest("s", null))
                .isInstanceOf(NullPointerException.class);
    }
}