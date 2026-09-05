package io.vykronis.incident;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST surface for incidents, consumed by the API gateway ({@code
 * /api/incidents}) and the incident UI. {@code POST /{incidentId}/investigate}
 * runs the investigation state machine (OPEN → INVESTIGATING → HYPOTHESIS_READY)
 * by delegating to the agent orchestrator (Phase 4 Unit 6).
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @GetMapping("/{incidentId}")
    public ResponseEntity<?> byId(@PathVariable String incidentId) {
        return service.findById(incidentId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(io.vykronis.common.api.ApiError.notFound(
                                "Incident not found: " + incidentId, java.util.UUID.randomUUID())));
    }

    @PostMapping("/{incidentId}/investigate")
    public ResponseEntity<?> investigate(@PathVariable String incidentId) {
        return ResponseEntity.ok(service.toSummary(service.investigate(incidentId)));
    }
}