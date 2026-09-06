package io.vykronis.incident;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.IncidentCandidate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes {@code obs.alerts} ({@link IncidentCandidate} produced by the
 * correlation engine) and turns each candidate into a new OPEN incident, or
 * refreshes an already-OPEN incident for the same service/environment.
 *
 * <p>Idempotency: if an incident is already OPEN for (serviceId, env) we update
 * its metrics/title instead of creating a duplicate. This keeps repeated
 * candidates for consecutive anomalous windows from spamming the timeline.</p>
 */
@Service
public class IncidentCandidateConsumer {

    private final IncidentRepository repository;
    private final VerificationService verificationService;

    public IncidentCandidateConsumer(IncidentRepository repository, VerificationService verificationService) {
        this.repository = repository;
        this.verificationService = verificationService;
    }

    @KafkaListener(topics = "obs.alerts", groupId = "incident-service")
    @Transactional
    public void onCandidate(IncidentCandidate candidate) {
        if (candidate == null) {
            return;
        }
        var all = repository.findByServiceIdOrderByDetectedAtDesc(candidate.serviceId());
        var existing = all.stream()
                .filter(i -> i.getEnv().equals(candidate.env().name()))
                .filter(i -> IncidentStatus.OPEN.name().equals(i.getStatus()))
                .findFirst();

        if (existing.isPresent()) {
            update(existing.get(), candidate);
            return;
        }

        // Phase 6 Unit 4: a candidate for a service that is mid-verification
        // means the remediation did not hold — fail it and learn, rather than
        // piling a duplicate incident on top of a VERIFYING one.
        var verifying = all.stream()
                .filter(i -> i.getEnv().equals(candidate.env().name()))
                .filter(i -> IncidentStatus.VERIFYING.name().equals(i.getStatus()))
                .findFirst();
        if (verifying.isPresent()) {
            verificationService.onReBreach(verifying.get());
            return;
        }

        create(candidate);
    }

    private void create(IncidentCandidate c) {
        Incident incident = new Incident(
                UUID.randomUUID().toString(),
                "correlation-engine",
                c.serviceId(),
                c.env().name(),
                c.severity().name(),
                IncidentStatus.OPEN.name(),
                titleFor(c),
                c.reason(),
                c.metrics() != null ? c.metrics().path("error_rate_max").asDouble(0.0) : null,
                c.metrics() != null ? c.metrics().path("error_count").asInt(0) : null,
                c.windowStart(),
                c.windowEnd(),
                null,
                c.metrics() != null ? c.metrics().toString() : null);
        repository.save(incident);
    }

    private void update(Incident incident, IncidentCandidate c) {
        incident.setSeverity(c.severity().name());
        incident.setStatus(IncidentStatus.OPEN.name());
        if (c.metrics() != null) {
            incident.setErrorRate(c.metrics().path("error_rate_max").asDouble(incident.getErrorRate() != null
                    ? incident.getErrorRate() : 0.0));
            incident.setErrorCount(c.metrics().path("error_count").asInt(
                    incident.getErrorCount() != null ? incident.getErrorCount() : 0));
            incident.setWindowEnd(c.windowEnd());
        }
        incident.markUpdated();
        repository.save(incident);
    }

    private String titleFor(IncidentCandidate c) {
        return "High error rate on " + c.serviceId() + " (" + c.severity().name() + ")";
    }
}
