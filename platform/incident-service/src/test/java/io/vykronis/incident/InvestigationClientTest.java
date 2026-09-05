package io.vykronis.incident;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The incident-service side of the investigation: one POST to the agent
 * orchestrator's {@code /api/investigations} that returns the hypothesis JSON
 * document (already schema-validated there). Any non-2xx response is an
 * {@link InvestigationUnavailableException} so the incident aggregation can
 * revert its state instead of persisting a half-finished investigation.
 */
class InvestigationClientTest {

    private static final String BODY =
            "{\"status\":\"HYPOTHESIS_READY\",\"evidence\":[{\"eventId\":\"ev-1\"}]}";

    private InvestigationClient clientWith(RestClient.Builder builder) {
        return new InvestigationClient(builder, "http://agent-orchestrator:8085");
    }

    private RestClient.Builder newBuilder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        return builder;
    }

    @Test
    void postsTheIncidentIdAndReturnsTheHypothesisJson() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InvestigationClient client = clientWith(builder);

        server.expect(requestTo("http://agent-orchestrator:8085/api/investigations"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json("{\"incidentId\":\"3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a\"}"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        String body = client.investigate("3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a");

        assertThat(body).contains("HYPOTHESIS_READY");
        server.verify();
    }

    @Test
    void surfacesOrchestratorFailuresAsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InvestigationClient client = clientWith(builder);

        server.expect(requestTo("http://agent-orchestrator:8085/api/investigations"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.investigate("3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a"))
                .isInstanceOf(InvestigationUnavailableException.class);
        server.verify();
    }
}