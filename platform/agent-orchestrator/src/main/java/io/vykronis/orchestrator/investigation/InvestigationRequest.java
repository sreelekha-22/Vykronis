package io.vykronis.orchestrator.investigation;

import java.util.UUID;

/**
 * Body of the investigation HTTP call the incident-service performs on
 * {@code POST /api/investigations}. Rejecting the request here (before any tool
 * effect) keeps contract errors cheap and outside the provider/fallback path.
 *
 * @param incidentId the incident under investigation
 */
public record InvestigationRequest(UUID incidentId) {
}