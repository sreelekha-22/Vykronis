package io.vykronis.orchestrator.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * End-to-end proof that a selected provider really completes a prompt over HTTP:
 * the Spring AI Ollama model is pointed at a {@link MockRestServiceServer} via the
 * shared {@link RestClient.Builder}, a chat-completion is served, and the answer
 * travels back through the {@link AiProvider} seam as an {@link AiResponse}.
 */
class OllamaProviderHttpIntegrationTest {

    @Test
    void completesAgainstMockedOllamaEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        AiProviderProperties props = new AiProviderProperties();
        props.setProvider(AiProviderName.OLLAMA);
        props.getOllama().setBaseUrl("http://localhost:11434");
        props.getOllama().setModel("llama3.2");

        SpringAiProvider provider = (SpringAiProvider) new ProviderSelectionConfig()
                .activeAiProvider(props, builder);

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "model": "llama3.2",
                          "createdAt": "2026-09-05T10:00:00Z",
                          "message": { "role": "assistant", "content": "payment-service v1.4.2 after rollout" },
                          "done": true
                        }
                        """, MediaType.APPLICATION_JSON));

        AiResponse response = provider.complete(new AiRequest("You are the Vykronis agent", "Investigate incident 42"));

        assertThat(response.provider()).isEqualTo("ollama");
        assertThat(response.content()).contains("v1.4.2");
        server.verify();
    }

    @Test
    void promptsContainTheSystemAndUserInstructions() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        AiProviderProperties props = new AiProviderProperties();
        props.setProvider(AiProviderName.OLLAMA);
        props.getOllama().setBaseUrl("http://localhost:11434");
        props.getOllama().setModel("llama3.2");

        SpringAiProvider provider = (SpringAiProvider) new ProviderSelectionConfig()
                .activeAiProvider(props, builder);

        server.expect(requestTo(containsString("/api/chat")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messages[0].role", is("system")))
                .andExpect(jsonPath("$.messages[0].content", is("You are the Vykronis agent")))
                .andExpect(jsonPath("$.messages[1].role", is("user")))
                .andExpect(jsonPath("$.messages[1].content", is("Investigate incident 42")))
                .andRespond(withSuccess(
                        "{\"model\":\"llama3.2\",\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"done\":true}",
                        MediaType.APPLICATION_JSON));

        provider.complete(new AiRequest("You are the Vykronis agent", "Investigate incident 42"));
        server.verify();
    }
}