package io.vykronis.incident;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The investigate state machine (Phase 4 Unit 6): an incident may only move
 * OPEN → INVESTIGATING → HYPOTHESIS_READY when a hypothesis was actually
 * produced. If the orchestrator is unavailable or cannot determine a
 * hypothesis, the incident reverts to its previous state rather than getting
 * stuck INVESTIGATING or swallowing a misleading HYPOTHESIS_READY.
 */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository repository;

    @Mock
    private InvestigationClient investigationClient;

    @InjectMocks
    private IncidentService service;

    private Incident openIncident() {
        return new Incident(
                "inc-1", "correlation-engine", "payment-service", "PROD", "HIGH",
                IncidentStatus.OPEN.name(), "High error rate", "desc",
                55.0, 42,
                Instant.parse("2026-09-03T10:00:00Z"),
                Instant.parse("2026-09-03T10:01:00Z"),
                null, null);
    }

    private String hypothesisJson() {
        return "{\"incidentId\":\"inc-1\",\"statement\":\"suspected\",\"confidence\":0.5,"
                + "\"affectedServiceId\":\"payment-service\",\"source\":\"fallback\","
                + "\"evidence\":[{\"eventId\":\"ev-1\",\"type\":\"LOG\",\"source\":\"payment\"}]}";
    }

    @Test
    void openIncidentBecomesHypothesisReadyWhenHypothesisIsProduced() {
        Incident incident = openIncident();
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(investigationClient.investigate("inc-1")).thenReturn(hypothesisJson());
        when(repository.save(incident)).thenReturn(incident);

        Incident result = service.investigate("inc-1");

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.HYPOTHESIS_READY.name());
        assertThat(result.getHypothesis()).contains("\"source\":\"fallback\"");
        assertThat(result.getInvestigatedAt()).isNotNull();
        verify(repository, org.mockito.Mockito.times(2)).save(incident);
    }

    @Test
    void alreadyHypothesisReadyIncidentCanBeReinvestigated() {
        Incident incident = openIncident();
        incident.setStatus(IncidentStatus.HYPOTHESIS_READY.name());
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(investigationClient.investigate("inc-1")).thenReturn(hypothesisJson());
        when(repository.save(incident)).thenReturn(incident);

        Incident result = service.investigate("inc-1");

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.HYPOTHESIS_READY.name());
    }

    @Test
    void revertsToPreviousStateWhenInvestigationFails() {
        Incident incident = openIncident();
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(investigationClient.investigate("inc-1"))
                .thenThrow(new InvestigationUnavailableException("orchestrator down"));
        when(repository.save(incident)).thenReturn(incident);

        assertThatThrownBy(() -> service.investigate("inc-1"))
                .isInstanceOf(InvestigationUnavailableException.class);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN.name());
        assertThat(incident.getHypothesis()).isNull();
    }

    @Test
    void throwsWhenIncidentIsMissing() {
        when(repository.findByIncidentId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.investigate("nope"))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void throwsWhenIncidentIsNotInAnInvestigableState() {
        Incident incident = openIncident();
        incident.setStatus(IncidentStatus.RESOLVED.name());
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.investigate("inc-1"))
                .isInstanceOf(InvestigationNotAllowedException.class);
    }
}