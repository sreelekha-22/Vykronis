package io.vykronis.incident;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 6 Unit 4 — the verification window turns a VERIFYING incident into a
 * terminal state and writes the outcome to the learn table:
 *
 * <pre>
 *   window elapsed (no re-breach) -> RESOLVED   + learn VERIFIED
 *   candidate arrives while VERIFYING -> FAILED + learn NOT_VERIFIED
 * </pre>
 *
 * Learn rows are keyed by incident id, so a sweep/failure can never write a
 * second row for the same incident.
 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private IncidentRepository repository;

    @Mock
    private RemediationLearnRepository learnRepository;

    @InjectMocks
    private VerificationService service;

    private Incident verifying() {
        Incident incident = new Incident(
                "inc-v1", "correlation-engine", "payment-service", "PROD", "HIGH",
                IncidentStatus.VERIFYING.name(), "High error rate", "desc",
                55.0, 42,
                Instant.parse("2026-09-06T10:00:00Z"),
                Instant.parse("2026-09-06T10:01:00Z"),
                null, null);
        incident.setRemediationCompletedAt(Instant.now().minusSeconds(70));
        incident.setVerifyDeadline(Instant.now().minusSeconds(5));
        return incident;
    }

    @Test
    void expiredVerifyingIncidentResolvesAndLearnsVerified() {
        Incident incident = verifying();
        when(repository.findByStatusOrderByDetectedAtDesc(IncidentStatus.VERIFYING.name()))
                .thenReturn(List.of(incident));
        when(learnRepository.existsByIncidentId("inc-v1")).thenReturn(false);

        service.sweepExpired();

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED.name());
        assertThat(incident.getResolvedAt()).isNotNull();
        verify(repository).save(incident);
        ArgumentCaptor<RemediationLearn> captor = ArgumentCaptor.forClass(RemediationLearn.class);
        verify(learnRepository).save(captor.capture());
        RemediationLearn learn = captor.getValue();
        assertThat(learn.incidentId()).isEqualTo("inc-v1");
        assertThat(learn.serviceId()).isEqualTo("payment-service");
        assertThat(learn.environment()).isEqualTo("PROD");
        assertThat(learn.action()).isEqualTo("ROLLBACK");
        assertThat(learn.result()).isEqualTo(VerificationOutcome.VERIFIED.name());
        assertThat(learn.windowStart()).isEqualTo(incident.getRemediationCompletedAt());
        assertThat(learn.windowEnd()).isEqualTo(incident.getVerifyDeadline());
        assertThat(learn.verifiedAt()).isNotNull();
    }

    @Test
    void verifyingIncidentWithFutureDeadlineIsLeftAlone() {
        Incident incident = verifying();
        incident.setVerifyDeadline(Instant.now().plusSeconds(500));
        when(repository.findByStatusOrderByDetectedAtDesc(IncidentStatus.VERIFYING.name()))
                .thenReturn(List.of(incident));

        service.sweepExpired();

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.VERIFYING.name());
        verify(repository, never()).save(incident);
        verify(learnRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reBreachFailsIncidentAndLearnsNotVerified() {
        Incident incident = verifying();
        when(learnRepository.existsByIncidentId("inc-v1")).thenReturn(false);

        service.onReBreach(incident);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.FAILED.name());
        verify(repository).save(incident);
        ArgumentCaptor<RemediationLearn> captor = ArgumentCaptor.forClass(RemediationLearn.class);
        verify(learnRepository).save(captor.capture());
        assertThat(captor.getValue().result()).isEqualTo(VerificationOutcome.NOT_VERIFIED.name());
    }

    @Test
    void reBreachOnNonVerifyingIncidentIsIgnored() {
        Incident incident = verifying();
        incident.setStatus(IncidentStatus.AUTO_APPROVED.name());

        service.onReBreach(incident);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.AUTO_APPROVED.name());
        verify(repository, never()).save(incident);
        verify(learnRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void incidentAlreadyLearnedIsNotRecordedTwice() {
        Incident incident = verifying();
        when(repository.findByStatusOrderByDetectedAtDesc(IncidentStatus.VERIFYING.name()))
                .thenReturn(List.of(incident));
        when(learnRepository.existsByIncidentId("inc-v1")).thenReturn(true);

        service.sweepExpired();

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED.name());
        verify(repository).save(incident);
        verify(learnRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}