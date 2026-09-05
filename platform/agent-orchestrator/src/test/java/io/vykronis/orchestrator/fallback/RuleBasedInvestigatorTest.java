package io.vykronis.orchestrator.fallback;

import io.vykronis.orchestrator.agent.InvestigationAgent;
import io.vykronis.orchestrator.hypothesis.EvidenceType;
import io.vykronis.orchestrator.hypothesis.Hypothesis;
import io.vykronis.orchestrator.hypothesis.HypothesisEvidence;
import io.vykronis.orchestrator.hypothesis.HypothesisSource;
import io.vykronis.orchestrator.tool.EvidenceSearchTool;
import io.vykronis.orchestrator.tool.IncidentDetailTool;
import io.vykronis.orchestrator.tool.IncidentListTool;
import io.vykronis.orchestrator.tool.ServiceEndpoint;
import io.vykronis.orchestrator.tool.ToolHttpClient;
import io.vykronis.orchestrator.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;

/**
 * The rule-based fallback (Phase 4 Unit 5) must NEVER fabricate evidence:
 * every claim it makes comes from the same allow-listed incident/evidence
 * tools the AI provider uses. It produces a {@code source=FALLBACK} hypothesis
 * from the real tool output, and quits with a typed error when there is no
 * evidence at all.
 */
class RuleBasedInvestigatorTest {

    private static final String INCIDENT = "3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a";
    private static final String INCD_INS = "http://incident-service:8084/api/incidents/" + INCIDENT;
    private static final String EVID = "http://event-service:8082/api/search/events";

    private RuleBasedInvestigator researcherWith(RestClient.Builder builder) {
        ToolHttpClient http = new ToolHttpClient(builder, List.of(
                new ServiceEndpoint("incident", "http://incident-service:8084", List.of("/api/incidents")),
                new ServiceEndpoint("event", "http://event-service:8082", List.of("/api/search"))));
        ToolRegistry registry = new ToolRegistry(List.of(
                new IncidentDetailTool(), new IncidentListTool(), new EvidenceSearchTool()));
        InvestigationAgent agent = new InvestigationAgent(registry, http);
        return new RuleBasedInvestigator(agent);
    }

    private String incidentBody() {
        return "{\"incidentId\":\"" + INCIDENT + "\",\"serviceId\":\"payment-service\",\"env\":\"PROD\","
                + "\"status\":\"INVESTIGATING\",\"title\":\"High error rate\","
                + "\"windowStart\":\"2026-09-05T10:00:00Z\",\"windowEnd\":\"2026-09-05T10:10:00Z\","
                + "\"metadata\":{\"error_rate_max\":55.0},\"description\":\"latency/ERR spike\"}";
    }

    @Test
    void slowestTracePicksTheAffectedServiceFromRealToolOutput() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuleBasedInvestigator researcher = researcherWith(builder);

        server.expect(requestTo(INCD_INS)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(incidentBody(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(EVID)))
                .andRespond(withSuccess(searchBody(), MediaType.APPLICATION_JSON));

        Hypothesis hypothesis = researcher.investigate(UUID.fromString(INCIDENT));

        assertThat(hypothesis.source()).isEqualTo(HypothesisSource.FALLBACK);
        assertThat(hypothesis.affectedServiceId()).isEqualTo("checkout-service");
        assertThat(hypothesis.evidence()).isNotEmpty();
        server.verify();
    }

    @Test
    void usesIncidentServiceWhenNoTraceIsAvailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuleBasedInvestigator researcher = researcherWith(builder);

        server.expect(requestTo(INCD_INS)).andRespond(withSuccess(incidentBody(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(EVID)))
                .andRespond(withSuccess("[{\"eventId\":\"ev-1\",\"type\":\"LOG\","
                        + "\"source\":\"payment-service\",\"payload\":{\"message\":\"timeout\"}}]",
                        MediaType.APPLICATION_JSON));

        Hypothesis hypothesis = researcher.investigate(UUID.fromString(INCIDENT));

        assertThat(hypothesis.affectedServiceId()).isEqualTo("payment-service");
        assertThat(hypothesis.evidence()).hasSize(1);
        server.verify();
    }

    @Test
    void attachesDeploymentVersionWhenPresentInIncidentOrEvidence() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuleBasedInvestigator researcher = researcherWith(builder);

        server.expect(requestTo(INCD_INS)).andRespond(withSuccess(incidentWithVersion(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(EVID)))
                .andRespond(withSuccess(searchBody(), MediaType.APPLICATION_JSON));

        Hypothesis hypothesis = researcher.investigate(UUID.fromString(INCIDENT));

        assertThat(hypothesis.affectedServiceVersion()).isEqualTo("2.3.1");
        server.verify();
    }

    @Test
    void throwsTypedErrorWhenThereIsNoEvidenceAtAll() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuleBasedInvestigator researcher = researcherWith(builder);

        server.expect(requestTo(INCD_INS)).andRespond(withSuccess(incidentBody(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(EVID)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> researcher.investigate(UUID.fromString(INCIDENT)))
                .isInstanceOf(InsufficientEvidenceException.class);
        server.verify();
    }

    @Test
    void mapsEvidenceRefsIntoTheHypothesisEvidenceShape() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuleBasedInvestigator researcher = researcherWith(builder);

        server.expect(requestTo(INCD_INS)).andRespond(withSuccess(incidentBody(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(EVID)))
                .andRespond(withSuccess(searchBody(), MediaType.APPLICATION_JSON));

        Hypothesis hypothesis = researcher.investigate(UUID.fromString(INCIDENT));

        HypothesisEvidence evidence = hypothesis.evidence().get(0);
        assertThat(evidence.type()).isEqualTo(EvidenceType.TRACE);
        assertThat(evidence.eventId()).isEqualTo("tr-1");
    }

    private String incidentWithVersion() {
        return "{\"incidentId\":\"" + INCIDENT + "\",\"serviceId\":\"payment-service\",\"env\":\"PROD\","
                + "\"status\":\"INVESTIGATING\",\"title\":\"High error rate\","
                + "\"windowStart\":\"2026-09-05T10:00:00Z\",\"windowEnd\":\"2026-09-05T10:10:00Z\","
                + "\"metadata\":{\"error_rate_max\":55.0,\"deploymentVersion\":\"2.3.1\"}}";
    }

    private String searchBody() {
        return "["
                + "{\"eventId\":\"tr-1\",\"type\":\"TRACE\",\"serviceId\":\"payment-service\","
                + "\"source\":\"payment-service\",\"payload\":{\"latency_ms\":120},\"timestamp\":\"2026-09-05T10:05:00Z\"},"
                + "{\"eventId\":\"tr-2\",\"type\":\"TRACE\",\"serviceId\":\"checkout-service\","
                + "\"source\":\"checkout-service\",\"payload\":{\"latency_ms\":980},\"timestamp\":\"2026-09-05T10:06:00Z\"}"
                + "]";
    }

    private static org.hamcrest.Matcher<String> containsString(String s) {
        return org.hamcrest.Matchers.containsString(s);
    }
}
