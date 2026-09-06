package io.vykronis.incident;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.IncidentCandidate;
import io.vykronis.contracts.model.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentCandidateConsumerTest {

    @Mock
    private IncidentRepository repository;

    @Mock
    private VerificationService verificationService;

    @InjectMocks
    private IncidentCandidateConsumer consumer;

    private IncidentCandidate candidate() {
        ObjectNode metrics = Json.mapper().createObjectNode()
                .put("error_rate_max", 55.0)
                .put("error_count", 42);
        return new IncidentCandidate(
                UUID.randomUUID(),
                Instant.parse("2026-09-03T10:01:00Z"),
                "payment-service",
                Env.PROD,
                Severity.HIGH,
                "error_rate 55.0 >= 20.0",
                Instant.parse("2026-09-03T10:00:00Z"),
                Instant.parse("2026-09-03T10:01:00Z"),
                metrics,
                null);
    }

    @Test
    void opensNewIncidentWhenNoneOpen() {
        when(repository.findByServiceIdOrderByDetectedAtDesc("payment-service")).thenReturn(List.of());

        consumer.onCandidate(candidate());

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(repository).save(captor.capture());
        Incident saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IncidentStatus.OPEN.name());
        assertThat(saved.getServiceId()).isEqualTo("payment-service");
        assertThat(saved.getSeverity()).isEqualTo(Severity.HIGH.name());
        assertThat(saved.getErrorRate()).isEqualTo(55.0);
        assertThat(saved.getErrorCount()).isEqualTo(42);
    }

    @Test
    void updatesExistingOpenIncidentInsteadOfDuplicating() {
        Incident open = new Incident();
        open.setSeverity(Severity.MEDIUM.name());
        open.setStatus(IncidentStatus.OPEN.name());
        open.setEnv(Env.PROD.name());
        open.setErrorCount(5);
        open.setErrorRate(25.0);
        when(repository.findByServiceIdOrderByDetectedAtDesc("payment-service"))
                .thenReturn(List.of(open));

        consumer.onCandidate(candidate());

        verify(repository, times(1)).save(open);
        assertThat(open.getSeverity()).isEqualTo(Severity.HIGH.name());
        assertThat(open.getErrorCount()).isEqualTo(42);
        assertThat(open.getErrorRate()).isEqualTo(55.0);
    }

    @Test
    void createsNewIncidentWhenOnlyResolvedExists() {
        Incident resolved = new Incident();
        resolved.setStatus(IncidentStatus.RESOLVED.name());
        resolved.setEnv(Env.PROD.name());
        when(repository.findByServiceIdOrderByDetectedAtDesc("payment-service"))
                .thenReturn(List.of(resolved));

        consumer.onCandidate(candidate());

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(repository).save(captor.capture());
        Incident saved = captor.getValue();
        assertThat(saved).isNotSameAs(resolved);
        assertThat(saved.getStatus()).isEqualTo(IncidentStatus.OPEN.name());
    }

    @Test
    void ignoresOpenIncidentForDifferentEnv() {
        Incident openDev = new Incident();
        openDev.setStatus(IncidentStatus.OPEN.name());
        openDev.setEnv(Env.DEV.name());
        when(repository.findByServiceIdOrderByDetectedAtDesc("payment-service"))
                .thenReturn(List.of(openDev));

        consumer.onCandidate(candidate());

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(repository).save(captor.capture());
        Incident saved = captor.getValue();
        assertThat(saved).isNotSameAs(openDev);
        assertThat(saved.getStatus()).isEqualTo(IncidentStatus.OPEN.name());
    }

    @Test
    void nullCandidateIsIgnored() {
        consumer.onCandidate(null);
        verifyNoInteractions(repository);
    }

    @Test
    void candidateWithoutMetricsStillOpensIncident() {
        when(repository.findByServiceIdOrderByDetectedAtDesc("payment-service")).thenReturn(List.of());

        IncidentCandidate noMetrics = new IncidentCandidate(
                UUID.randomUUID(),
                Instant.parse("2026-09-03T10:01:00Z"),
                "payment-service",
                Env.PROD,
                Severity.LOW,
                "reason",
                Instant.parse("2026-09-03T10:00:00Z"),
                Instant.parse("2026-09-03T10:01:00Z"),
                null,
                null);

        consumer.onCandidate(noMetrics);

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(repository).save(captor.capture());
        Incident saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IncidentStatus.OPEN.name());
        assertThat(saved.getErrorRate()).isNull();
        assertThat(saved.getErrorCount()).isNull();
    }

    @Test
    void candidateWhileVerifyingFailsTheIncidentAndLearns() {
        Incident verifying = new Incident();
        verifying.setStatus(IncidentStatus.VERIFYING.name());
        verifying.setEnv(Env.PROD.name());
        when(repository.findByServiceIdOrderByDetectedAtDesc("payment-service"))
                .thenReturn(List.of(verifying));

        consumer.onCandidate(candidate());

        verify(verificationService).onReBreach(verifying);
        verify(repository, never()).save(any());
        verifyNoMoreInteractions(repository);
    }
}
