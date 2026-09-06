package io.vykronis.remediation.store;

import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.RemediationAction;
import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationOutcome;
import io.vykronis.contracts.model.RemediationResult;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted execution record keyed by {@code commandId} — the idempotency key.
 * Replaying a {@link RemediationCommand} with an already-recorded command id
 * returns the stored {@link RemediationResult} without touching docker.
 *
 * @param commandId    the command id (unique, idempotency key)
 * @param incidentId   incident the remediation belongs to
 * @param action       ROLLBACK / RESTART
 * @param serviceId    target service
 * @param environment  target environment
 * @param targetVersion version the command aimed to restore
 * @param subject      approver identity that unlocked the action
 * @param outcome      COMPLETED / FAILED
 * @param detail       executor output / error details
 * @param completedAt  when the executor finished
 */
public record RemediationRecord(
        UUID commandId,
        String incidentId,
        RemediationAction action,
        String serviceId,
        Env environment,
        String targetVersion,
        String subject,
        RemediationOutcome outcome,
        String detail,
        Instant completedAt
) {

    public static RemediationRecord from(RemediationCommand command, RemediationOutcome outcome,
                                         String detail, Instant completedAt) {
        return new RemediationRecord(
                command.commandId(),
                command.incidentId(),
                command.action(),
                command.serviceId(),
                command.environment(),
                command.targetVersion(),
                command.subject(),
                outcome,
                detail,
                completedAt);
    }

    public RemediationResult asResult() {
        return new RemediationResult(
                UUID.randomUUID(),
                commandId,
                incidentId,
                outcome,
                detail,
                completedAt);
    }
}