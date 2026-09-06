package io.vykronis.incident;

import io.vykronis.contracts.model.RemediationOutcome;
import io.vykronis.contracts.model.RemediationResult;
import io.vykronis.contracts.Topics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumes {@link RemediationResult} on {@code obs.remediation} and advances a
 * REMEDIATING incident: COMPLETED → VERIFYING (opening the verification
 * window), FAILED → FAILED. The local docker run actually modifying the service
 * is executed by remediation-service; "only after policy" was already
 * guaranteed because commands are only issued from {@link IncidentService} once
 * the policy decision granted the action.
 *
 * <p>Replays are no-ops: results are only applied while the incident is
 * REMEDIATING, so a stale/duplicate result can never clobber VERIFYING,
 * RESOLVED or a newer FAILED.</p>
 */
@Service
public class RemediationResultConsumer {

    private final IncidentRepository repository;
    private final int verifyWindowSeconds;

    public RemediationResultConsumer(IncidentRepository repository,
                                     @Value("${vykronis.verify-window.seconds:60}") int verifyWindowSeconds) {
        this.repository = repository;
        this.verifyWindowSeconds = verifyWindowSeconds;
    }

    @KafkaListener(topics = Topics.REMEDIATION, groupId = "incident-service-remediation",
            containerFactory = "remediationResultListenerContainerFactory")
    @Transactional
    public void onResult(RemediationResult result) {
        if (result == null) {
            return;
        }
        repository.findByIncidentId(result.incidentId()).ifPresent(incident -> {
            if (!IncidentStatus.REMEDIATING.name().equals(incident.getStatus())) {
                return;
            }
            if (result.outcome() == RemediationOutcome.COMPLETED) {
                incident.setStatus(IncidentStatus.VERIFYING.name());
                incident.setVerifyDeadline(Instant.now().plusSeconds(verifyWindowSeconds));
            } else {
                incident.setStatus(IncidentStatus.FAILED.name());
            }
            incident.setRemediationOutcome(result.outcome().name());
            incident.setRemediationCompletedAt(Instant.now());
            incident.markUpdated();
            repository.save(incident);
        });
    }
}