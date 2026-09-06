package io.vykronis.incident;

import io.vykronis.contracts.model.RemediationOutcome;
import io.vykronis.contracts.model.RemediationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 6 Unit 3 — the remediation result consumer advances a REMEDIATING
 * incident: COMPLETED → VERIFYING, FAILED → FAILED. Results for incidents that
 * are not mid-remediation (or unknown) are ignored so replays are no-ops and a
 * stale result can never clobber a later state.
 */
@ExtendWith(MockitoExtension.class)
class RemediationResultConsumerTest {

    @Mock
    private IncidentRepository repository;

    @InjectMocks
    private RemediationResultConsumer consumer;

    private Incident remediating() {
        Incident incident = new Incident(
                "inc-9", "correlation-engine", "payment-service", "PROD", "HIGH",
                IncidentStatus.REMEDIATING.name(), "High error rate", "desc",
                55.0, 42,
                Instant.parse("2026-09-06T10:00:00Z"),
                Instant.parse("2026-09-06T10:01:00Z"),
                null, null);
        incident.setRemediationCommandId(UUID.randomUUID().toString());
        return incident;
    }

    private RemediationResult result(RemediationOutcome outcome) {
        return new RemediationResult(
                UUID.randomUUID(), UUID.randomUUID(), "inc-9", outcome,
                "docker compose done", Instant.parse("2026-09-06T10:03:00Z"));
    }

    @Test
    void completedResultMovesIncidentToVerifying() {
        Incident incident = remediating();
        when(repository.findByIncidentId("inc-9")).thenReturn(Optional.of(incident));
        when(repository.save(incident)).thenReturn(incident);

        consumer.onResult(result(RemediationOutcome.COMPLETED));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.VERIFYING.name());
        assertThat(incident.getRemediationOutcome()).isEqualTo("COMPLETED");
        assertThat(incident.getRemediationCompletedAt()).isNotNull();
        verify(repository).save(incident);
    }

    @Test
    void failedResultMovesIncidentToFailed() {
        Incident incident = remediating();
        when(repository.findByIncidentId("inc-9")).thenReturn(Optional.of(incident));
        when(repository.save(incident)).thenReturn(incident);

        consumer.onResult(result(RemediationOutcome.FAILED));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.FAILED.name());
        assertThat(incident.getRemediationOutcome()).isEqualTo("FAILED");
    }

    @Test
    void resultForANonRemediatingIncidentIsIgnored() {
        Incident incident = remediating();
        incident.setStatus(IncidentStatus.VERIFYING.name());
        when(repository.findByIncidentId("inc-9")).thenReturn(Optional.of(incident));

        consumer.onResult(result(RemediationOutcome.FAILED));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.VERIFYING.name());
        verify(repository, never()).save(incident);
    }

    @Test
    void unknownIncidentIsIgnored() {
        when(repository.findByIncidentId("inc-9")).thenReturn(Optional.empty());

        consumer.onResult(result(RemediationOutcome.COMPLETED));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nullResultIsIgnored() {
        consumer.onResult(null);

        verify(repository, never()).findByIncidentId(org.mockito.ArgumentMatchers.anyString());
    }
}