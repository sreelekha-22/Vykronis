package io.vykronis.orchestrator.investigation;

import java.util.UUID;

/**
 * Raised when an investigation is asked for an incident the allow-listed
 * incident.detail tool cannot load (reported not found by the incident-service).
 * HTTPS: 404.
 */
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(UUID incidentId) {
        super("Incident not found: " + incidentId);
    }
}