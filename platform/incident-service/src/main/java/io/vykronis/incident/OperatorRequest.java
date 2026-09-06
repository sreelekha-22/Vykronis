package io.vykronis.incident;

/**
 * Request body for {@code POST /api/incidents/{id}/remediation} and
 * {@code POST /api/incidents/{id}/approve} — the acting subject.
 */
public record OperatorRequest(OperatorActor subject) {
}
