package io.vykronis.orchestrator;

import io.vykronis.orchestrator.agent.InvestigationAgent;
import io.vykronis.orchestrator.ai.AiGateway;
import io.vykronis.orchestrator.ai.AiRequest;
import io.vykronis.orchestrator.ai.AiResponse;
import io.vykronis.orchestrator.ai.NoOpAiProvider;
import io.vykronis.orchestrator.fallback.InsufficientEvidenceException;
import io.vykronis.orchestrator.fallback.InvestigationCoordinator;
import io.vykronis.orchestrator.fallback.RuleBasedInvestigator;
import io.vykronis.orchestrator.hypothesis.Hypothesis;
import io.vykronis.orchestrator.hypothesis.HypothesisSource;
import io.vykronis.orchestrator.hypothesis.HypothesisValidator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 4 Unit 8 — end-to-end invariants of the investigation decision seam,
 * driven RED first through the REAL stack: real {@link HypothesisValidator}
 * (hypothesis.schema.json on the classpath), real {@link RuleBasedInvestigator}
 * (only the tool HTTP client is mocked via {@link InvestigationAgent}), and the
 * real {@link AiGateway} with {@link NoOpAiProvider} for the {@code none}
 * provider. Proves the two hard guarantees of the phase:
 *
 * <ol>
 *   <li>Raw provider output is NEVER the answer: schema-invalid JSON (or valid
 *       JSON for the WRONG incident) cannot reach a caller — it falls back to
 *       the rule-based path.</li>
 *   <li>With provider {@code none}, the platform still investigates from REAL
 *       tool evidence, never fabricates (empty evidence -&gt;
 *       {@link InsufficientEvidenceException}), and never attaches a deployment
 *       version that evidence/incident metadata did not carry.</li>
 * </ol>
 */
class Phase4FallbackEndToEndTest {

    private static final UUID INCIDENT = UUID.fromString("3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a");
    private static final UUID OTHER = UUID.fromString("4f3c2a7e-2c2d-4e3f-9f5a-2c3d4e5f6a7b");

    private static final AiRequest REQUEST = new AiRequest("system", "user");

    private String incident(boolean withDeploymentVersion) {
        return "{\"incidentId\":\"" + INCIDENT + "\",\"serviceId\":\"payment-service\",\"status\":\"OPEN\","
                + "\"title\":\"High error rate\",\n"
                + (withDeploymentVersion
                        ? "\"metadata\":{\"deploymentId\":\"dep-1\",\"deploymentVersion\":\"3.2.1\"},"
                        : "")
                + "\"windowStart\":\"2026-09-05T10:00:00Z\",\"windowEnd\":\"2026-09-05T10:10:00Z\"}";
    }

    private String evidence() {
        return "["
                + "{\"eventId\":\"ev-t1\",\"source\":\"payment-service\",\"serviceId\":\"payment-service\","
                + "\"env\":\"PROD\",\"type\":\"TRACE\",\"payload\":{\"latency_ms\":2400},"
                + "\"timestamp\":\"2026-09-05T10:00:05Z\",\"status\":\"OPEN\"},"
                + "{\"eventId\":\"ev-t2\",\"source\":\"payment-service\",\"serviceId\":\"payment-service\","
                + "\"env\":\"PROD\",\"type\":\"TRACE\",\"payload\":{\"latency_ms\":1200},"
                + "\"timestamp\":\"2026-09-05T10:00:06Z\",\"status\":\"OPEN\"}"
                + "]";
    }

    private InvestigationAgent agentReturning(String incidentBody, String evidenceBody) {
        InvestigationAgent agent = mock(InvestigationAgent.class);
        when(agent.invoke(anyString(), any())).thenAnswer(invocation -> {
            String toolId = invocation.getArgument(0);
            return "incident.detail".equals(toolId) ? incidentBody : evidenceBody;
        });
        return agent;
    }

    private InvestigationCoordinator coordinator(AiGateway gateway, InvestigationAgent agent) {
        return new InvestigationCoordinator(
                gateway,
                new HypothesisValidator(),
                new RuleBasedInvestigator(agent));
    }

    @Test
    void noneProviderFallsBackToRealToolEvidence() {
        AiGateway gateway = new AiGateway(new NoOpAiProvider());
        InvestigationAgent agent = agentReturning(incident(false), evidence());

        Hypothesis result = coordinator(gateway, agent).investigate(INCIDENT, REQUEST);

        assertThat(result.source()).isEqualTo(HypothesisSource.FALLBACK);
        assertThat(result.incidentId()).isEqualTo(INCIDENT);
        assertThat(result.affectedServiceId()).isEqualTo("payment-service");
        assertThat(result.affectedServiceVersion()).isNull();
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.evidence().get(0).type().name()).isEqualTo("TRACE");
    }

    @Test
    void noEvidenceEndsInInsufficientEvidenceNotAnEmptyHypothesis() {
        AiGateway gateway = new AiGateway(new NoOpAiProvider());
        InvestigationAgent agent = agentReturning(incident(false), "[]");

        assertThatThrownBy(() -> coordinator(gateway, agent).investigate(INCIDENT, REQUEST))
                .isInstanceOf(InsufficientEvidenceException.class);
    }

    @Test
    void schemaInvalidProviderOutputNeverReachesTheCaller() {
        AiGateway gateway = mock(AiGateway.class);
        when(gateway.complete(any(AiRequest.class))).thenReturn(new AiResponse("ollama", "{"
                + "\"incidentId\":\"" + INCIDENT + "\","
                + "\"statement\":\"THE MODEL WAS HERE\",\"confidence\":0.99,"
                + "\"affectedServiceId\":\"checkout-service\",\"source\":\"ai\","
                + "\"evidence\":[],\"hallucinatedField\":true}"));
        InvestigationAgent agent = agentReturning(incident(false), evidence());

        Hypothesis result = coordinator(gateway, agent).investigate(INCIDENT, REQUEST);

        assertThat(result.source()).isEqualTo(HypothesisSource.FALLBACK);
        assertThat(result.statement()).doesNotContain("THE MODEL WAS HERE");
        assertThat(result.evidence()).hasSize(2);
    }

    @Test
    void schemaValidButWrongIncidentOutputFallsBack() {
        AiGateway gateway = mock(AiGateway.class);
        when(gateway.complete(any(AiRequest.class))).thenReturn(new AiResponse("ollama", "{"
                + "\"incidentId\":\"" + OTHER + "\","
                + "\"statement\":\"hallucinated for another incident\",\"confidence\":0.99,"
                + "\"affectedServiceId\":\"checkout-service\",\"source\":\"ai\","
                + "\"evidence\":[{\"eventId\":\"ev-x\",\"type\":\"LOG\",\"source\":\"checkout\"}]}"));
        InvestigationAgent agent = agentReturning(incident(false), evidence());

        Hypothesis result = coordinator(gateway, agent).investigate(INCIDENT, REQUEST);

        assertThat(result.source()).isEqualTo(HypothesisSource.FALLBACK);
        assertThat(result.incidentId()).isEqualTo(INCIDENT);
        assertThat(result.statement()).doesNotContain("another incident");
    }

    @Test
    void fallbackAttachesDeploymentVersionOnlyWhenIncidentCarriedIt() {
        AiGateway gateway = new AiGateway(new NoOpAiProvider());
        InvestigationAgent agent = agentReturning(incident(true), evidence());

        Hypothesis result = coordinator(gateway, agent).investigate(INCIDENT, REQUEST);

        assertThat(result.source()).isEqualTo(HypothesisSource.FALLBACK);
        assertThat(result.affectedServiceVersion()).isEqualTo("3.2.1");
    }
}