package io.vykronis.contracts.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Execution report for one {@link RemediationCommand}, produced by
 * remediation-service and consumed by the incident service to advance the
 * incident past its verify window (R -> VERIFYING -> RESOLVED | FAILED).
 *
 * @param resultId    unique result id (replay of a command returns the stored result)
 * @param commandId   the command this reports on (idempotency key)
 * @param incidentId  incident the remediation belongs to
 * @param outcome     COMPLETED / FAILED
 * @param detail      executor output / error details
 * @param completedAt when the executor finished
 */
public record RemediationResult(
        @JsonProperty("resultId") UUID resultId,
        @JsonProperty("commandId") UUID commandId,
        @JsonProperty("incidentId") String incidentId,
        @JsonProperty("outcome") RemediationOutcome outcome,
        @JsonProperty("detail") String detail,
        @JsonProperty("completedAt") Instant completedAt
) {
    public RemediationResult {
        if (resultId == null) {
            throw new IllegalArgumentException("resultId must not be null");
        }
        if (commandId == null) {
            throw new IllegalArgumentException("commandId must not be null");
        }
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId must not be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
    }
}