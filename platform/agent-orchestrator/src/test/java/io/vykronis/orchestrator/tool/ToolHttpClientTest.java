package io.vykronis.orchestrator.tool;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The tools an LLM may use are enforced by {@link ToolHttpClient} IDENTITY-FIRST:
 * an endpoint service name must be configured and the requested path must be
 * under that endpoint's allow-list BEFORE any HTTP connection is attempted. An
 * agent can ask the model for "docker", "kubectl", … — there is no such tool and
 * no such path; the violation is raised client-side and the server is never
 * contacted (Phase 4 Unit 2: never Docker, never Kubernetes).
 */
class ToolHttpClientTest {

    private static final String INCIDENT_UUID = "3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a";

    private void build(List<ServiceEndpoint> endpoints, ServerSetup setup) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ToolHttpClient client = new ToolHttpClient(builder, endpoints);
        setup.run(server, client);
    }

    @FunctionalInterface
    private interface ServerSetup {
        void run(MockRestServiceServer server, ToolHttpClient client);
    }

    private List<ServiceEndpoint> allowListedEndpoints() {
        return List.of(
                new ServiceEndpoint("incident", "http://incident-service:8084", List.of("/api/incidents")),
                new ServiceEndpoint("event", "http://event-service:8082", List.of("/api/search")));
    }

    @Test
    void allowsIncidentDetailAndReturnsBody() {
        build(allowListedEndpoints(), (server, client) -> {
            server.expect(requestTo("http://incident-service:8084/api/incidents/" + INCIDENT_UUID))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            "{\"incidentId\":\"" + INCIDENT_UUID + "\",\"status\":\"INVESTIGATING\"}",
                            MediaType.APPLICATION_JSON));

            String body = client.get("incident", "/api/incidents/" + INCIDENT_UUID, Map.of());

            assertThat(body).contains(INCIDENT_UUID).contains("INVESTIGATING");
            server.verify();
        });
    }

    @Test
    void allowsEventSearchWithinAllowListedPrefix() {
        build(allowListedEndpoints(), (server, client) -> {
            server.expect(requestTo(containsString("/api/search/events")))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(queryParam("from", "2026-09-05T10:00:00Z"))
                    .andExpect(queryParam("to", "2026-09-05T10:10:00Z"))
                    .andExpect(queryParam("traceId", "tr-1"))
                    .andRespond(withSuccess(
                            "[{\"eventId\":\"ev-1\",\"type\":\"TRACE\"}]",
                            MediaType.APPLICATION_JSON));

            String body = client.get("event", "/api/search/events", Map.of(
                    "from", "2026-09-05T10:00:00Z",
                    "to", "2026-09-05T10:10:00Z",
                    "traceId", "tr-1"));

            assertThat(body).contains("ev-1").contains("TRACE");
            server.verify();
        });
    }

    @Test
    void blockDockerInvocationBeforeAnyHttpCall() {
        build(allowListedEndpoints(), (server, client) ->
                assertThatThrownBy(() -> client.get("event", "/api/docker/containers", Map.of()))
                        .isInstanceOf(ToolEndpointViolationException.class)
                        .hasMessageContaining("/api/docker/containers"));
    }

    @Test
    void blockKubernetesApiInvocation() {
        build(allowListedEndpoints(), (server, client) ->
                assertThatThrownBy(() -> client.get("incident", "/api/v1/pods", Map.of()))
                        .isInstanceOf(ToolEndpointViolationException.class)
                        .hasMessageContaining("/api/v1/pods"));
    }

    @Test
    void blockPathTraversalThroughAllowListedPrefix() {
        build(allowListedEndpoints(), (server, client) ->
                assertThatThrownBy(() -> client.get("incident", "/api/incidents/../../etc/passwd", Map.of()))
                        .isInstanceOf(ToolEndpointViolationException.class));
    }

    @Test
    void blockSiblingPathThatSharesPrefixLetters() {
        build(allowListedEndpoints(), (server, client) ->
                assertThatThrownBy(() -> client.get("incident", "/api/incident-services/admin", Map.of()))
                        .isInstanceOf(ToolEndpointViolationException.class));
    }

    @Test
    void blockUnknownEndpointName() {
        build(allowListedEndpoints(), (server, client) ->
                assertThatThrownBy(() -> client.get("database-admin", "/api/incidents", Map.of()))
                        .isInstanceOf(ToolEndpointViolationException.class));
    }

    @Test
    void constructorsRejectConfigurationWithoutAnyAllowList() {
        assertThatThrownBy(() -> new ToolHttpClient(RestClient.builder(),
                List.of(new ServiceEndpoint("incident", "http://incident-service:8084", List.of()))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}