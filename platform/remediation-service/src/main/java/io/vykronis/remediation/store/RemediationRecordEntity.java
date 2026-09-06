package io.vykronis.remediation.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing the idempotency store. One row per executed command id.
 */
@Entity
@Table(name = "remediation_records")
public class RemediationRecordEntity {

    @Id
    @Column(name = "command_id", nullable = false, updatable = false)
    private UUID commandId;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private String incidentId;

    @Column(name = "action", nullable = false, updatable = false, length = 16)
    private String action;

    @Column(name = "service_id", nullable = false, updatable = false)
    private String serviceId;

    @Column(name = "environment", nullable = false, updatable = false, length = 16)
    private String environment;

    @Column(name = "target_version", updatable = false)
    private String targetVersion;

    @Column(name = "subject", updatable = false)
    private String subject;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected RemediationRecordEntity() {
    }

    public RemediationRecordEntity(RemediationRecord record) {
        this.commandId = record.commandId();
        this.incidentId = record.incidentId();
        this.action = record.action().name();
        this.serviceId = record.serviceId();
        this.environment = record.environment().name();
        this.targetVersion = record.targetVersion();
        this.subject = record.subject();
        this.outcome = record.outcome().name();
        this.detail = record.detail();
        this.completedAt = record.completedAt();
    }

    public RemediationRecord toRecord() {
        return new RemediationRecord(
                commandId,
                incidentId,
                io.vykronis.contracts.model.RemediationAction.valueOf(action),
                serviceId,
                io.vykronis.contracts.model.Env.valueOf(environment),
                targetVersion,
                subject,
                io.vykronis.contracts.model.RemediationOutcome.valueOf(outcome),
                detail,
                completedAt);
    }
}