package io.vykronis.orchestrator.fallback;

import com.fasterxml.jackson.databind.JsonNode;
import io.vykronis.common.json.Json;
import io.vykronis.orchestrator.agent.InvestigationAgent;
import io.vykronis.orchestrator.hypothesis.EvidenceType;
import io.vykronis.orchestrator.hypothesis.Hypothesis;
import io.vykronis.orchestrator.hypothesis.HypothesisEvidence;
import io.vykronis.orchestrator.hypothesis.HypothesisSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rule-based fallback (Phase 4 Unit 5). When the AI provider is
 * {@code none} or unavailable, this produces a hypothesis from REAL tool output
 * only — it never fabricates a service, a version, or an evidence reference.
 *
 * <p>Heuristic (a deployment-join style attribution over the allow-listed
 * incident/evidence tools): the affected service is the one serving the slowest
 * TRACE inside the incident's window (the largest {@code latency_ms} in the
 * trace payload); if there are no traces, it is the incident's own serviceId.
 * A deployment version is attached only when one is actually present (incident
 * {@code metadata.deploymentVersion}). If an investigation surfaces no evidence
 * at all, {@link InsufficientEvidenceException} is thrown rather than emitting
 * an empty (schema-invalid) hypothesis.</p>
 *
 * <p>Deliberately uses the same {@link InvestigationAgent} effect path as a
 * provider — only allow-listed incident/event/search endpoints are ever
 * contacted.</p>
 */
@Service
public class RuleBasedInvestigator {

    private static final String INCIDENT_DETAIL = "incident.detail";
    private static final String EVIDENCE_SEARCH = "evidence.search";

    private final InvestigationAgent agent;

    public RuleBasedInvestigator(InvestigationAgent agent) {
        this.agent = agent;
    }

    /** Runs the fallback for {@code incidentId}, returning a FALLBACK hypothesis. */
    public Hypothesis investigate(UUID incidentId) {
        JsonNode incident = fetchIncident(incidentId);
        String serviceId = text(incident, "serviceId");
        String version = deploymentVersion(incident);
        Instant from = instant(incident, "windowStart");
        Instant to = instant(incident, "windowEnd");

        List<JsonNode> evidence = search(from, to, serviceId);

        if (evidence.isEmpty()) {
            throw new InsufficientEvidenceException(
                    "No observability evidence found in window for incident " + incidentId);
        }

        List<JsonNode> traces = evidence.stream()
                .filter(hit -> "TRACE".equals(text(hit, "type")))
                .toList();

        String affectedService = traces.isEmpty() ? serviceId : slowestService(traces);
        List<HypothesisEvidence> refs = toEvidence(evidence);

        return new Hypothesis(
                incidentId,
                statement(affectedService),
                "Rule-based fallback: slowest-trace attribution over "
                        + refs.size() + " evidence items",
                0.5,
                affectedService,
                version,
                HypothesisSource.FALLBACK,
                refs);
    }

    private JsonNode fetchIncident(UUID incidentId) {
        String body = agent.invoke(INCIDENT_DETAIL, Map.of("incidentId", incidentId.toString()));
        try {
            return Json.mapper().readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse incident detail for investigation", e);
        }
    }

    private List<JsonNode> search(Instant from, Instant to, String serviceId) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("from", from.toString());
        args.put("to", to.toString());
        if (serviceId != null) {
            args.put("serviceId", serviceId);
        }
        String body = agent.invoke(EVIDENCE_SEARCH, args);
        try {
            JsonNode root = Json.mapper().readTree(body);
            List<JsonNode> hits = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(hits::add);
            }
            return hits;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse evidence search for investigation", e);
        }
    }

    /** The service serving the largest trace latency in the window. */
    private String slowestService(List<JsonNode> traces) {
        double max = -1;
        String service = null;
        for (JsonNode trace : traces) {
            double latency = trace.path("payload").path("latency_ms").asDouble(0.0);
            if (service == null || latency > max) {
                max = latency;
                service = text(trace, "serviceId");
            }
        }
        return service != null ? service : text(traces.get(0), "serviceId");
    }

    private List<HypothesisEvidence> toEvidence(List<JsonNode> hits) {
        List<HypothesisEvidence> result = new ArrayList<>();
        for (JsonNode hit : hits) {
            result.add(new HypothesisEvidence(
                    text(hit, "eventId"),
                    evidenceType(text(hit, "type")),
                    text(hit, "source"),
                    evidenceSummary(hit)));
        }
        return result;
    }

    private static EvidenceType evidenceType(String raw) {
        try {
            return EvidenceType.valueOf(raw == null ? "" : raw);
        } catch (IllegalArgumentException e) {
            return EvidenceType.LOG;
        }
    }

    private static String evidenceSummary(JsonNode hit) {
        JsonNode payload = hit.path("payload");
        if (payload.isTextual()) {
            return payload.asText();
        }
        String latency = payload.path("latency_ms").asText(null);
        if (latency != null) {
            return "latency_ms=" + latency;
        }
        String message = payload.path("message").asText(null);
        return message;
    }

    private static String deploymentVersion(JsonNode incident) {
        if (incident != null && incident.hasNonNull("metadata")) {
            JsonNode metadata = incident.get("metadata");
            if (metadata.hasNonNull("deploymentVersion")) {
                return metadata.get("deploymentVersion").asText();
            }
        }
        return null;
    }

    private static String statement(String service) {
        return service == null
                ? "Incident under active investigation (rule-based fallback)"
                : "Elevated error/latency most plausibly originating from " + service;
    }

    private static String text(JsonNode node, String field) {
        return node == null || node.isMissingNode() || !node.has(field) || node.get(field).isNull()
                ? null
                : node.get(field).asText();
    }

    private static Instant instant(JsonNode node, String field) {
        String raw = text(node, field);
        return raw == null ? null : Instant.parse(raw);
    }
}
