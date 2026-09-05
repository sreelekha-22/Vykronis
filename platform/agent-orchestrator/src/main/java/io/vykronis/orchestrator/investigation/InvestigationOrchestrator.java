package io.vykronis.orchestrator.investigation;

import com.fasterxml.jackson.databind.JsonNode;
import io.vykronis.common.json.Json;
import io.vykronis.orchestrator.agent.InvestigationAgent;
import io.vykronis.orchestrator.ai.AiRequest;
import io.vykronis.orchestrator.fallback.InvestigationCoordinator;
import io.vykronis.orchestrator.hypothesis.Hypothesis;
import io.vykronis.orchestrator.tool.ToolSpec;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The agent-orchestrator's investigation entry point (Phase 4 Unit 6). Loads an
 * incident through the allow-listed {@code incident.detail} tool — never past a
 * raw/unauthenticated caller — and hands the verified context (incident JSON +
 * tool catalogue) to the {@link InvestigationCoordinator}. Coordinator decides
 * AI-provider-first, rule-based fallback otherwise, and the resulting
 * validated {@link Hypothesis} is returned to the caller.
 */
@Service
public class InvestigationOrchestrator {

    private static final String INCIDENT_DETAIL = "incident.detail";

    private final InvestigationAgent agent;
    private final InvestigationCoordinator coordinator;

    public InvestigationOrchestrator(InvestigationAgent agent, InvestigationCoordinator coordinator) {
        this.agent = agent;
        this.coordinator = coordinator;
    }

    public Hypothesis investigate(UUID incidentId) {
        String detail = agent.invoke(INCIDENT_DETAIL, Map.of("incidentId", incidentId.toString()));
        return coordinator.investigate(incidentId, buildRequest(incidentId, detail));
    }

    private AiRequest buildRequest(UUID incidentId, String detail) {
        JsonNode incident = parseIncident(incidentId, detail);
        String catalogue = agent.availableTools().stream()
                .map(this::describeTool)
                .collect(Collectors.joining("\n"));

        String system = "You are Vykronis' incident investigation agent. Your effect surface is "
                + "exactly the allow-listed tools below; never propose anything beyond them.\n\n"
                + "Tool catalogue:\n" + catalogue
                + "\n\nInvestigate the incident and answer ONLY with one JSON object that obeys "
                + "hypothesis.schema.json: incidentId, statement, summary, confidence, "
                + "affectedServiceId, affectedServiceVersion (only if evidence shows it), source "
                + "(ai|fallback), evidence (non-empty; each eventId/type/source/summary).";

        String user = "Investigate this incident:\n" + incident.toPrettyString()
                + "\n\nProduce a hypothesis as a single JSON object.";

        return new AiRequest(system, user);
    }

    private JsonNode parseIncident(UUID incidentId, String detail) {
        try {
            JsonNode node = Json.mapper().readTree(detail);
            String status = node.path("status").asText("200");
            String code = node.path("code").asText("");
            boolean looksLikeError = node.isObject()
                    && ("404".equals(status) || "NOT_FOUND".equals(code));
            if (node.path("incidentId").isMissingNode() || looksLikeError) {
                throw new IncidentNotFoundException(incidentId);
            }
            return node;
        } catch (IncidentNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IncidentNotFoundException(incidentId);
        }
    }

    private String describeTool(ToolSpec tool) {
        return "- " + tool.id() + " (" + String.join(", ", tool.parameters()) + "): " + tool.description();
    }
}