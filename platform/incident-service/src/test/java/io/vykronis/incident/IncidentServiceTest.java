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

    @Mock
    private PolicyClient policyClient;

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

    private Incident hypothesisReady(String env) {
        Incident incident = new Incident(
                "inc-1", "correlation-engine", "payment-service", env, "HIGH",
                IncidentStatus.HYPOTHESIS_READY.name(), "High error rate", "desc",
                55.0, 42,
                Instant.parse("2026-09-03T10:00:00Z"),
                Instant.parse("2026-09-03T10:01:00Z"),
                null, null);
        incident.setHypothesis(hypothesisJson());
        return incident;
    }

    private static OperatorActor approver() {
        return OperatorActor.user("ops", "vykronis-approver");
    }

    private static OperatorActor human() {
        return OperatorActor.user("alice");
    }

    @Test
    void prodRemediationAwaitsApprovalWhenPolicyRequiresIt() {
        Incident incident = hypothesisReady("PROD");
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(policyClient.evaluate("inc-1", "ROLLBACK", "PROD", approver()))
                .thenReturn(new PolicyEvaluation("REQUIRE_APPROVAL", "prod needs an approver"));
        when(repository.save(incident)).thenReturn(incident);

        Incident result = service.requestRemediation("inc-1", approver());

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.AWAITING_APPROVAL.name());
        assertThat(result.getPolicyDecision()).isEqualTo("REQUIRE_APPROVAL");
        assertThat(result.getRequestedAt()).isNotNull();
        assertThat(result.getApprovedAt()).isNull();
    }

    @Test
    void prodRemediationAutoApprovesWhenPolicyAllows() {
        Incident incident = hypothesisReady("PROD");
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(policyClient.evaluate("inc-1", "ROLLBACK", "PROD", approver()))
                .thenReturn(new PolicyEvaluation("ALLOW", "explicit allow"));
        when(repository.save(incident)).thenReturn(incident);

        Incident result = service.requestRemediation("inc-1", approver());

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.AUTO_APPROVED.name());
        assertThat(result.getPolicyDecision()).isEqualTo("ALLOW");
        assertThat(result.getApprovedAt()).isNotNull();
        assertThat(result.getApprovedBy()).isEqualTo("ops");
    }

    @Test
    void devRemediationAutoApprovesWithoutAnApprover() {
        Incident incident = hypothesisReady("DEV");
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(policyClient.evaluate("inc-1", "ROLLBACK", "DEV", human()))
                .thenReturn(new PolicyEvaluation("ALLOW", "dev rollbacks auto"));
        when(repository.save(incident)).thenReturn(incident);

        Incident result = service.requestRemediation("inc-1", human());

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.AUTO_APPROVED.name());
        assertThat(result.getApprovedBy()).isEqualTo("alice");
    }

    @Test
    void deniedRemediationFailsTheIncident() {
        Incident incident = hypothesisReady("PROD");
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(policyClient.evaluate("inc-1", "ROLLBACK", "PROD", human()))
                .thenReturn(new PolicyEvaluation("DENY", "no approver"));
        when(repository.save(incident)).thenReturn(incident);

        Incident result = service.requestRemediation("inc-1", human());

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.FAILED.name());
        assertThat(result.getPolicyDecision()).isEqualTo("DENY");
        assertThat(result.getApprovedAt()).isNull();
    }

    @Test
    void remediationRequiresAHypothesis() {
        Incident incident = openIncident();
        incident.setStatus(IncidentStatus.OPEN.name());
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.requestRemediation("inc-1", approver()))
                .isInstanceOf(RemediationNotAllowedException.class);
        org.mockito.Mockito.verifyNoInteractions(policyClient);
    }

    @Test
    void leavesIncidentEligibleWhenPolicyIsUnavailable() {
        Incident incident = hypothesisReady("PROD");
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(policyClient.evaluate("inc-1", "ROLLBACK", "PROD", approver()))
                .thenThrow(new PolicyUnavailableException("policy down"));

        assertThatThrownBy(() -> service.requestRemediation("inc-1", approver()))
                .isInstanceOf(PolicyUnavailableException.class);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.HYPOTHESIS_READY.name());
        assertThat(incident.getPolicyDecision()).isNull();
    }

    @Test
    void approvalMovesAwaitingIncidentToAutoApproved() {
        Incident incident = hypothesisReady("PROD");
        incident.setStatus(IncidentStatus.AWAITING_APPROVAL.name());
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(repository.save(incident)).thenReturn(incident);

        Incident result = service.approve("inc-1", approver());

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.AUTO_APPROVED.name());
        assertThat(result.getApprovedAt()).isNotNull();
        assertThat(result.getApprovedBy()).isEqualTo("ops");
    }

    @Test
    void approvalRejectsAnIncidentThatIsNotAwaiting() {
        Incident incident = hypothesisReady("PROD");
        incident.setStatus(IncidentStatus.AUTO_APPROVED.name());
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.approve("inc-1", approver()))
                .isInstanceOf(ApprovalNotAllowedException.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(incident);
    }
}