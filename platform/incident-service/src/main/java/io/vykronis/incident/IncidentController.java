package io.vykronis.incident;

import io.vykronis.common.api.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST surface for incidents, consumed by the API gateway ({@code
 * /api/incidents}) and the incident UI.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentRepository repository;

    public IncidentController(IncidentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        List<Incident> incidents = (status == null || status.isBlank())
                ? repository.findAllByOrderByDetectedAtDesc()
                : repository.findByStatusOrderByDetectedAtDesc(status.toUpperCase());
        return incidents.stream().map(this::toSummary).collect(Collectors.toList());
    }

    @GetMapping("/{incidentId}")
    public ResponseEntity<?> byId(@PathVariable String incidentId) {
        return repository.findByIncidentId(incidentId)
                .<ResponseEntity<?>>map(i -> ResponseEntity.ok(toSummary(i)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiError.notFound("Incident not found: " + incidentId, java.util.UUID.randomUUID())));
    }

    private Map<String, Object> toSummary(Incident i) {
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
        m.put("metadata", safeJson(i.getMetadata()));
        return m;
    }

    private Object safeJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return io.vykronis.common.json.Json.mapper().readTree(raw);
        } catch (Exception e) {
            return raw;
        }
    }
}
