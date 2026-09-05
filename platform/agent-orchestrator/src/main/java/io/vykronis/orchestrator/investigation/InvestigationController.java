package io.vykronis.orchestrator.investigation;

import io.vykronis.orchestrator.hypothesis.Hypothesis;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes investigation over HTTP for the incident-service
 * ({@code POST /api/investigations}). Returns the validated hypothesis JSON on
 * 200; 404/422/400 handled by {@link InvestigationApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/investigations")
public class InvestigationController {

    private final InvestigationOrchestrator orchestrator;

    public InvestigationController(InvestigationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ResponseEntity<Hypothesis> investigate(@RequestBody InvestigationRequest request) {
        if (request == null || request.incidentId() == null) {
            throw new IllegalArgumentException("incidentId is required");
        }
        return ResponseEntity.ok(orchestrator.investigate(request.incidentId()));
    }
}