package io.vykronis.incident;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Phase 6 Unit 4 — the verification window. Once a remediation completes, the
 * incident sits in VERIFYING for {@code vykronis.verify-window.seconds}. This
 * service turns the verification outcome into a terminal state and writes it to
 * the learn table ({@code remediation_learn}):
 *
 * <pre>
 *   window elapsed with no re-breach  -> RESOLVED    + learn VERIFIED
 *   candidate arrives while VERIFYING -> FAILED      + learn NOT_VERIFIED
 * </pre>
 *
 * Learn rows are keyed by incident id, so sweeping twice or a re-breach after
 * the sweep can never produce a second row for the same incident.
 */
@Service
public class VerificationService {

    private final IncidentRepository repository;
    private final RemediationLearnRepository learnRepository;

    public VerificationService(IncidentRepository repository, RemediationLearnRepository learnRepository) {
        this.repository = repository;
        this.learnRepository = learnRepository;
    }

    /** Resolve every VERIFYING incident whose window has elapsed (sweep). */
    @Transactional
    public void sweepExpired() {
        Instant now = Instant.now();
        List<Incident> verifying = repository.findByStatusOrderByDetectedAtDesc(IncidentStatus.VERIFYING.name());
        for (Incident incident : verifying) {
            if (incident.getVerifyDeadline() != null && !incident.getVerifyDeadline().isAfter(now)) {
                incident.resolve();
                repository.save(incident);
                recordLearn(incident, VerificationOutcome.VERIFIED, now);
            }
        }
    }

    /**
     * A new candidate arrived while this incident was VERIFYING: the service
     * re-broke during the window, so the remediation did not hold.
     */
    @Transactional
    public void onReBreach(Incident incident) {
        if (!IncidentStatus.VERIFYING.name().equals(incident.getStatus())) {
            return;
        }
        incident.setStatus(IncidentStatus.FAILED.name());
        incident.markUpdated();
        repository.save(incident);
        recordLearn(incident, VerificationOutcome.NOT_VERIFIED, Instant.now());
    }

    private void recordLearn(Incident incident, VerificationOutcome outcome, Instant verifiedAt) {
        if (learnRepository.existsByIncidentId(incident.getIncidentId())) {
            return;
        }
        Instant windowStart = incident.getRemediationCompletedAt() != null
                ? incident.getRemediationCompletedAt()
                : incident.getUpdatedAt();
        learnRepository.save(new RemediationLearn(
                incident.getIncidentId(),
                incident.getServiceId(),
                incident.getEnv(),
                "ROLLBACK",
                outcome.name(),
                windowStart,
                incident.getVerifyDeadline(),
                verifiedAt));
    }
}