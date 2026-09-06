package io.vykronis.incident;

import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.RemediationAction;
import io.vykronis.contracts.model.RemediationCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The investigate state machine (Phase 4 Unit 6): an incident may only move
 * OPEN → INVESTIGATING → HYPOTHESIS_READY when a hypothesis was actually
 * produced. If the orchestrator is unavailable or cannot determine a
 * hypothesis, the incident reverts to its previous state rather than getting
 * stuck INVESTIGATING or swallowing a misleading HYPOTHESIS_READY.
 *
 * <p>Phase 6 Unit 3: an approved remediation issues a {@link RemediationCommand}
 * on {@code obs.remediation} and the incident moves to REMEDIATING; the result
 * consumer (see {@code RemediationResultConsumerTest}) advances it later.</p>
 */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository repository;

    @Mock
    private InvestigationClient investigationClient;

    @Mock
    private PolicyClient policyClient;

    @Mock
    private KafkaTemplate<String, RemediationCommand> kafkaTemplate;

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
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void prodRemediationAutoApprovesWhenPolicyAllows() {
        Incident incident = hypothesisReady("PROD");
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(policyClient.evaluate("inc-1", "ROLLBACK", "PROD", approver()))
                .thenReturn(new PolicyEvaluation("ALLOW", "explicit allow"));
        when(repository.save(incident)).thenReturn(incident);

        Incident result = service.requestRemediation("inc-1", approver());

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.REMEDIATING.name());
        assertThat(result.getPolicyDecision()).isEqualTo("ALLOW");
        assertThat(result.getApprovedAt()).isNotNull();
        assertThat(result.getApprovedBy()).isEqualTo("ops");
        assertThat(result.getRemediationCommandId()).isNotNull();
        assertRollbackCommandIssued(result.getRemediationCommandId(), "PROD", "ops");
    }

    private void assertRollbackCommandIssued(String commandId, String env, String approvedBy) {
        ArgumentCaptor<RemediationCommand> captor = ArgumentCaptor.forClass(RemediationCommand.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq(Topics.REMEDIATION),
                org.mockito.ArgumentMatchers.eq(commandId), captor.capture());
        assertThat(captor.getValue().incidentId()).isEqualTo("inc-1");
        assertThat(captor.getValue().action()).isEqualTo(RemediationAction.ROLLBACK);
        assertThat(captor.getValue().serviceId()).isEqualTo("payment-service");
        assertThat(captor.getValue().environment().name()).isEqualTo(env);
        assertThat(captor.getValue().subject()).isEqualTo(approvedBy);
        assertThat(captor.getValue().issuedAt()).isNotNull();
    }

    @Test
    void devRemediationAutoApprovesWithoutAnApprover() {
        Incident incident = hypothesisReady("DEV");
        when(repository.findByIncidentId("inc-1")).thenReturn(Optional.of(incident));
        when(policyClient.evaluate("inc-1", "ROLLBACK", "DEV", human()))
                .thenReturn(new PolicyEvaluation("ALLOW", "dev rollbacks auto"));
        when(repository.save(incident)).thenReturn(incident);

        Incident result = service.requestRemediation("inc-1", human());

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.REMEDIATING.name());
        assertThat(result.getApprovedBy()).isEqualTo("alice");
        assertRollbackCommandIssued(result.getRemediationCommandId(), "DEV", "alice");
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
        verifyNoInteractions(kafkaTemplate);
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

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.REMEDIATING.name());
        assertThat(result.getApprovedAt()).isNotNull();
        assertThat(result.getApprovedBy()).isEqualTo("ops");
        assertThat(result.getRemediationCommandId()).isNotNull();
        assertRollbackCommandIssued(result.getRemediationCommandId(), "PROD", "ops");
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