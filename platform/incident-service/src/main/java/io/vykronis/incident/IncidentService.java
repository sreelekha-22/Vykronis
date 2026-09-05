package io.vykronis.incident;

import io.vykronis.common.json.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Incident read + investigate flows. {@link #investigate} runs the Phase 4
 * Unit 6 state machine:
 *
 * <pre>OPEN / HYPOTHESIS_READY → INVESTIGATING → HYPOTHESIS_READY</pre>
 *
 * The hypothesis is stored only when the orchestrator actually produced one. If
 * the orchestrator is unavailable or the investigation yields nothing, the
 * incident reverts to its previous state — it never gets stuck INVESTIGATING
 * and never reports HYPOTHESIS_READY without a hypothesis.
 */
@Service
public class IncidentService {

    private static final Set<String> INVESTIGABLE = Set.of(
            IncidentStatus.OPEN.name(),
            IncidentStatus.HYPOTHESIS_READY.name());

    private final IncidentRepository repository;
    private final InvestigationClient investigationClient;

    public IncidentService(IncidentRepository repository, InvestigationClient investigationClient) {
        this.repository = repository;
        this.investigationClient = investigationClient;
    }

    public List<Map<String, Object>> list(String status) {
        List<Incident> incidents = (status == null || status.isBlank())
                ? repository.findAllByOrderByDetectedAtDesc()
                : repository.findByStatusOrderByDetectedAtDesc(status.toUpperCase());
        return incidents.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public Optional<Map<String, Object>> findById(String incidentId) {
        return repository.findByIncidentId(incidentId).map(this::toSummary);
    }

    @Transactional
    public Incident investigate(String incidentId) {
        Incident incident = repository.findByIncidentId(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
        if (!INVESTIGABLE.contains(incident.getStatus())) {
            throw new InvestigationNotAllowedException(
                    "Incident " + incidentId + " is " + incident.getStatus()
                            + "; only OPEN or HYPOTHESIS_READY can be investigated");
        }

        String previous = incident.getStatus();
        incident.setStatus(IncidentStatus.INVESTIGATING.name());
        incident.markUpdated();
        repository.save(incident);

        try {
            String hypothesis = investigationClient.investigate(incidentId);
            incident.setHypothesis(hypothesis);
            incident.setInvestigatedAt(Instant.now());
            incident.setStatus(IncidentStatus.HYPOTHESIS_READY.name());
            incident.markUpdated();
            return repository.save(incident);
        } catch (RuntimeException e) {
            incident.setStatus(previous);
            incident.markUpdated();
            repository.save(incident);
            throw e;
        }
    }

    public Map<String, Object> toSummary(Incident i) {
        Map<String, Object> m = new HashMap<>();
        m.put("incidentId", i.getIncidentId());
        m.put("serviceId", i.getServiceId());
        m.put("env", i.getEnv());
        m.put("severity", i.getSeverity());
        m.put("status", i.getStatus());
        m.put("title", i.getTitle());
        m.put("description", i.getDescription());
        m.put("errorRate", i.getErrorRate());
        m.put("errorCount", i.getErrorCount());
        m.put("windowStart", i.getWindowStart() != null ? i.getWindowStart().toString() : null);
        m.put("windowEnd", i.getWindowEnd() != null ? i.getWindowEnd().toString() : null);
        m.put("detectedAt", i.getDetectedAt() != null ? i.getDetectedAt().toString() : null);
        m.put("resolvedAt", i.getResolvedAt() != null ? i.getResolvedAt().toString() : null);
        m.put("investigatedAt", i.getInvestigatedAt() != null ? i.getInvestigatedAt().toString() : null);
        m.put("metadata", safeJson(i.getMetadata()));
        m.put("hypothesis", safeJson(i.getHypothesis()));
        return m;
    }

    private Object safeJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // Plain Map/List/String (not a Jackson-2 JsonNode) so Spring's
            // Jackson-3 converter serializes it as a JSON tree, not a bean.
            return Json.mapper().readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }
}