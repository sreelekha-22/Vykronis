package io.vykronis.contracts.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * A remediation order issued by the incident service once policy has granted
 * the action (AUTO_APPROVED). Emitted on the {@code obs.remediation} topic;
 * remediation-service executes it against the local deployment target and
 * replies with a {@link RemediationResult}.
 *
 * <p>Key on {@code commandId} so replaying a command is a no-op — the
 * executor caches results per command id (idempotency key).</p>
 *
 * @param commandId     unique id; the idempotency key for the executor
 * @param incidentId    incident the remediation belongs to
 * @param action        ROLLBACK / RESTART
 * @param serviceId     target service (PARTITIONING key)
 * @param environment   target environment (DEV / PROD)
 * @param targetVersion optional version to restore/roll back to
 * @param issuedAt      when the order was issued
 * @param subject       approver identity that unlocked the action
 */
public record RemediationCommand(
        @JsonProperty("commandId") UUID commandId,
        @JsonProperty("incidentId") String incidentId,
        @JsonProperty("action") RemediationAction action,
        @JsonProperty("serviceId") String serviceId,
        @JsonProperty("environment") Env environment,
        @JsonProperty("targetVersion") String targetVersion,
        @JsonProperty("issuedAt") Instant issuedAt,
        @JsonProperty("subject") String subject
) {
    public RemediationCommand {
        if (commandId == null) {
            throw new IllegalArgumentException("commandId must not be null");
        }
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (serviceId == null) {
            throw new IllegalArgumentException("serviceId must not be null");
        }
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("issuedAt must not be null");
        }
    }
}