package io.vykronis.orchestrator.agent;

import io.vykronis.orchestrator.tool.EvidenceSearchTool;
import io.vykronis.orchestrator.tool.IncidentDetailTool;
import io.vykronis.orchestrator.tool.IncidentListTool;
import io.vykronis.orchestrator.tool.ServiceEndpoint;
import io.vykronis.orchestrator.tool.ToolHttpClient;
import io.vykronis.orchestrator.tool.ToolRegistry;
import io.vykronis.orchestrator.tool.ToolSpec;
import io.vykronis.orchestrator.tool.UnknownToolException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The agent exposes ONLY the allow-listed tool set and the only effect of an
 * invocation is an HTTP call to an allow-listed incident/event/search endpoint.
 * There is intentionally no tool that can reach Docker or Kubernetes — not by
 * name, not by path (Phase 4 Unit 2).
 */
class InvestigationAgentTest {

    private static final String INCIDENT_UUID = "3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a";
    private static final String PREFIX_EVIDENCE = "evidence.search";
    private static final String PREFIX_INCIDENT_DETAIL = "incident.detail";
    private static final String PREFIX_INCIDENT_LIST = "incident.list";

    private InvestigationAgent newAgentWith(RestClient.Builder builder) {
        ToolHttpClient http = new ToolHttpClient(builder, List.of(
                new ServiceEndpoint("incident", "http://incident-service:8084", List.of("/api/incidents")),
                new ServiceEndpoint("event", "http://event-service:8082", List.of("/api/search"))));
        ToolRegistry registry = new ToolRegistry(List.of(
                new IncidentDetailTool(), new IncidentListTool(), new EvidenceSearchTool()));
        return new InvestigationAgent(registry, http);
    }

    @Test
    void exposesExactlyTheAllowListedToolSet() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        InvestigationAgent agent = newAgentWith(builder);

        List<ToolSpec> tools = agent.availableTools();

        assertThat(tools).extracting(ToolSpec::id)
                .containsExactly(PREFIX_EVIDENCE, PREFIX_INCIDENT_DETAIL, PREFIX_INCIDENT_LIST);
    }

    @Test
    void rejectsDockerOrKubernetesToolNameBeforeAnyHttpCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        InvestigationAgent agent = newAgentWith(builder);

        assertThatThrownBy(() -> agent.invoke("docker.containers", Map.of()))
                .isInstanceOf(UnknownToolException.class);
        assertThatThrownBy(() -> agent.invoke("kubectl.pods", Map.of()))
                .isInstanceOf(UnknownToolException.class);
        assertThatThrownBy(() -> agent.invoke("helm.upgrade", Map.of()))
                .isInstanceOf(UnknownToolException.class);
    }

    @Test
    void incidentDetailContactsOnlyTheIncidentEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InvestigationAgent agent = newAgentWith(builder);

        server.expect(requestTo("http://incident-service:8084/api/incidents/" + INCIDENT_UUID))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"incidentId\":\"" + INCIDENT_UUID + "\",\"status\":\"INVESTIGATING\"}",
                        MediaType.APPLICATION_JSON));

        String body = agent.invoke(PREFIX_INCIDENT_DETAIL, Map.of("incidentId", INCIDENT_UUID));

        assertThat(body).contains(INCIDENT_UUID).contains("INVESTIGATING");
        server.verify();
    }

    @Test
    void incidentListContactsOnlyTheIncidentEndpointEvenWhenFiltered() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InvestigationAgent agent = newAgentWith(builder);

        server.expect(requestTo(containsString("/api/incidents")))
                .andExpect(queryParam("status", "INVESTIGATING"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        String body = agent.invoke(PREFIX_INCIDENT_LIST, Map.of("status", "INVESTIGATING"));

        assertThat(body).isEqualTo("[]");
        server.verify();
    }

    @Test
    void evidenceSearchContactsOnlyTheSearchEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InvestigationAgent agent = newAgentWith(builder);

        server.expect(requestTo(containsString("/api/search/events")))
                .andExpect(queryParam("from", "2026-09-05T10:00:00Z"))
                .andExpect(queryParam("to", "2026-09-05T10:10:00Z"))
                .andExpect(queryParam("traceId", "tr-1"))
                .andRespond(withSuccess(
                        "[{\"eventId\":\"ev-1\",\"type\":\"LOG\",\"source\":\"payment-service\"}]",
                        MediaType.APPLICATION_JSON));

        String body = agent.invoke(PREFIX_EVIDENCE, Map.of(
                "from", "2026-09-05T10:00:00Z",
                "to", "2026-09-05T10:10:00Z",
                "traceId", "tr-1"));

        assertThat(body)
                .contains("\"eventId\":\"ev-1\"")
                .contains("\"source\":\"payment-service\"");
        server.verify();
    }

    @Test
    void evidenceSearchWithOptionalFiltersOnlyAddsPresentOnes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InvestigationAgent agent = newAgentWith(builder);

        server.expect(requestTo(containsString("/api/search/events")))
                .andExpect(queryParam("from", "2026-09-05T10:00:00Z"))
                .andExpect(queryParam("to", "2026-09-05T10:10:00Z"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        agent.invoke(PREFIX_EVIDENCE, Map.of(
                "from", "2026-09-05T10:00:00Z",
                "to", "2026-09-05T10:10:00Z"));

        server.verify();
        assertThat(agent.availableTools())
                .extracting(ToolSpec::id)
                .containsExactly(PREFIX_EVIDENCE, PREFIX_INCIDENT_DETAIL, PREFIX_INCIDENT_LIST);
    }

    @Test
    void evidenceSearchRequiresTheTimeWindow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        InvestigationAgent agent = newAgentWith(builder);

        assertThatThrownBy(() -> agent.invoke(PREFIX_EVIDENCE, Map.of("traceId", "tr-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    void incidentDetailRequiresAValidIncidentIdentifier() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        InvestigationAgent agent = newAgentWith(builder);

        assertThatThrownBy(() -> agent.invoke(PREFIX_INCIDENT_DETAIL, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incidentId");
        assertThatThrownBy(() -> agent.invoke(PREFIX_INCIDENT_DETAIL, Map.of("incidentId", "../../etc/passwd")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsIntentInToolResultShapesForTheProviderContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        InvestigationAgent agent = newAgentWith(builder);

        assertThat(agent.availableTools()).extracting(ToolSpec::id, ToolSpec::parameters)
                .contains(
                        tuple(PREFIX_EVIDENCE, List.of("from", "to", "serviceId?", "type?", "traceId?")),
                        tuple(PREFIX_INCIDENT_DETAIL, List.of("incidentId")));
    }
}