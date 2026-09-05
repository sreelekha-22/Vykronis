package io.vykronis.orchestrator.investigation;

import io.vykronis.orchestrator.agent.InvestigationAgent;
import io.vykronis.orchestrator.ai.AiRequest;
import io.vykronis.orchestrator.fallback.InvestigationCoordinator;
import io.vykronis.orchestrator.hypothesis.Hypothesis;
import io.vykronis.orchestrator.hypothesis.HypothesisSource;
import io.vykronis.orchestrator.tool.ToolSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InvestigationOrchestrator} is the agent-orchestrator entry point the
 * incident-service calls. It fetches the incident via the allow-listed tools
 * and hands the context to the {@link InvestigationCoordinator} (provider first,
 * rule-based fallback otherwise), so the model/fallback NEVER sees an incident
 * the tools could not load.
 */
class InvestigationOrchestratorTest {

    private static final UUID INCIDENT = UUID.fromString("3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a");

    private final InvestigationAgent agent = mock(InvestigationAgent.class);
    private final InvestigationCoordinator coordinator = mock(InvestigationCoordinator.class);

    private String incidentBody() {
        return "{\"incidentId\":\"" + INCIDENT + "\",\"serviceId\":\"payment-service\",\"env\":\"PROD\","
                + "\"status\":\"OPEN\",\"title\":\"High error rate\","
                + "\"windowStart\":\"2026-09-05T10:00:00Z\",\"windowEnd\":\"2026-09-05T10:10:00Z\"}";
    }

    @Test
    void loadsTheIncidentThroughToolsAndDelegatesToTheCoordinator() {
        when(agent.invoke("incident.detail", java.util.Map.of("incidentId", INCIDENT.toString())))
                .thenReturn(incidentBody());
        when(agent.availableTools()).thenReturn(List.of(
                new ToolSpec("evidence.search", "Searches evidence", List.of("from", "to"))));
        Hypothesis expected = new Hypothesis(INCIDENT, "statement", null, 0.5,
                "payment-service", null, HypothesisSource.FALLBACK, List.of());
        when(coordinator.investigate(any(UUID.class), any(AiRequest.class))).thenReturn(expected);

        InvestigationOrchestrator orchestrator = new InvestigationOrchestrator(agent, coordinator);
        Hypothesis result = orchestrator.investigate(INCIDENT);

        assertThat(result).isEqualTo(expected);
        verify(coordinator).investigate(eq(INCIDENT), any(AiRequest.class));
    }

    @Test
    void embedsIncidentContextAndToolCatalogueInThePrompts() {
        when(agent.invoke("incident.detail", java.util.Map.of("incidentId", INCIDENT.toString())))
                .thenReturn(incidentBody());
        when(agent.availableTools()).thenReturn(List.of(
                new ToolSpec("evidence.search", "Searches evidence", List.of("from", "to"))));
        Hypothesis expected = new Hypothesis(INCIDENT, "statement", null, 0.5,
                "payment-service", null, HypothesisSource.FALLBACK, List.of());
        org.mockito.ArgumentCaptor<AiRequest> captor = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        when(coordinator.investigate(org.mockito.ArgumentMatchers.eq(INCIDENT), captor.capture()))
                .thenReturn(expected);

        InvestigationOrchestrator orchestrator = new InvestigationOrchestrator(agent, coordinator);
        orchestrator.investigate(INCIDENT);

        AiRequest request = captor.getValue();
        assertThat(request.userPrompt()).contains("payment-service");
        assertThat(request.systemPrompt()).contains("evidence.search");
    }

    @Test
    void throwsWhenTheIncidentCannotBeLoaded() {
        String notFound = "{\"status\":404,\"code\":\"NOT_FOUND\",\"message\":\"Incident not found\"}";
        when(agent.invoke("incident.detail", java.util.Map.of("incidentId", INCIDENT.toString())))
                .thenReturn(notFound);

        InvestigationOrchestrator orchestrator = new InvestigationOrchestrator(agent, coordinator);

        assertThatThrownBy(() -> orchestrator.investigate(INCIDENT))
                .isInstanceOf(IncidentNotFoundException.class);
    }
}