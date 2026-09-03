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

    public IncidentCandidateConsumer(IncidentRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "obs.alerts", groupId = "incident-service")
    @Transactional
    public void onCandidate(IncidentCandidate candidate) {
        if (candidate == null) {
            return;
        }
        var existing = repository.findByServiceIdOrderByDetectedAtDesc(candidate.serviceId())
                .stream()
                .filter(i -> i.getEnv().equals(candidate.env().name()))
                .filter(i -> IncidentStatus.OPEN.name().equals(i.getStatus()))
                .findFirst();

        if (existing.isPresent()) {
            update(existing.get(), candidate);
        } else {
            create(candidate);
        }
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
