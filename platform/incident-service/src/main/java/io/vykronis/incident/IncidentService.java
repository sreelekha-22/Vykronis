package io.vykronis.incident;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.RemediationAction;
import io.vykronis.contracts.model.RemediationCommand;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Incident read + investigate flows. {@link #investigate} runs the Phase 4
 * Unit 6 state machine:
 *
 * <pre>OPEN / HYPOTHESIS_READY → INVESTIGATING → HYPOTHESIS_READY</pre>
 *
 * The hypothesis is stored only when the orchestrator actually produced one. If
 * the orchestrator is unavailable or the investigation yields nothing, the
 * incident reverts to its previous state — it never gets stuck INVESTIGATING
 * and never reports HYPOTHESIS_READY without a hypothesis.
 */
@Service
public class IncidentService {

    private static final Set<String> INVESTIGABLE = Set.of(
            IncidentStatus.OPEN.name(),
            IncidentStatus.HYPOTHESIS_READY.name());

    private final IncidentRepository repository;
    private final InvestigationClient investigationClient;
    private final PolicyClient policyClient;
    private final KafkaTemplate<String, RemediationCommand> remediationCommandKafkaTemplate;

    public IncidentService(IncidentRepository repository, InvestigationClient investigationClient,
                           PolicyClient policyClient,
                           KafkaTemplate<String, RemediationCommand> remediationCommandKafkaTemplate) {
        this.repository = repository;
        this.investigationClient = investigationClient;
        this.policyClient = policyClient;
        this.remediationCommandKafkaTemplate = remediationCommandKafkaTemplate;
    }

    public List<Map<String, Object>> list(String status) {
        List<Incident> incidents = (status == null || status.isBlank())
                ? repository.findAllByOrderByDetectedAtDesc()
                : repository.findByStatusOrderByDetectedAtDesc(status.toUpperCase());
        return incidents.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public Optional<Map<String, Object>> findById(String incidentId) {
        return repository.findByIncidentId(incidentId).map(this::toSummary);
    }

    @Transactional
    public Incident investigate(String incidentId) {
        Incident incident = repository.findByIncidentId(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
        if (!INVESTIGABLE.contains(incident.getStatus())) {
            throw new InvestigationNotAllowedException(
                    "Incident " + incidentId + " is " + incident.getStatus()
                            + "; only OPEN or HYPOTHESIS_READY can be investigated");
        }

        String previous = incident.getStatus();
        incident.setStatus(IncidentStatus.INVESTIGATING.name());
        incident.markUpdated();
        repository.save(incident);

        try {
            String hypothesis = investigationClient.investigate(incidentId);
            incident.setHypothesis(hypothesis);
            incident.setInvestigatedAt(Instant.now());
            incident.setStatus(IncidentStatus.HYPOTHESIS_READY.name());
            incident.markUpdated();
            return repository.save(incident);
        } catch (RuntimeException e) {
            incident.setStatus(previous);
            incident.markUpdated();
            repository.save(incident);
            throw e;
        }
    }

    /**
     * Phase 5 Unit 4: the policy gate + approval transition. A remediation may
     * only be requested once a hypothesis exists; the acting subject is sent to
     * the policy service, whose decision drives the incident:
     *
     * <pre>
     *   ALLOW            -> AUTO_APPROVED -> REMEDIATING (command issued)
     *   REQUIRE_APPROVAL -> AWAITING_APPROVAL (a human approver must click approve)
     *   DENY             -> FAILED (remediation rejected; human decides next)
     * </pre>
     *
     * If the policy service cannot be reached the incident stays HYPOTHESIS_READY
     * (eligible) and never transitions into a policy-derived state.
     */
    @Transactional
    public Incident requestRemediation(String incidentId, OperatorActor subject) {
        Incident incident = repository.findByIncidentId(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
        if (!IncidentStatus.HYPOTHESIS_READY.name().equals(incident.getStatus())) {
            throw new RemediationNotAllowedException(
                    "Incident " + incidentId + " is " + incident.getStatus()
                            + "; only HYPOTHESIS_READY incidents can request remediation");
        }
        PolicyEvaluation evaluation = policyClient.evaluate(
                incidentId, PolicyClient.ACTION_ROLLBACK, incident.getEnv(), subject);
        incident.setPolicyDecision(evaluation.decision());
        incident.setRequestedAt(Instant.now());
        switch (evaluation.decision()) {
            case "ALLOW" -> approveAndIssue(incident, subject);
            case "REQUIRE_APPROVAL" -> incident.setStatus(IncidentStatus.AWAITING_APPROVAL.name());
            default -> incident.setStatus(IncidentStatus.FAILED.name());
        }
        incident.markUpdated();
        return repository.save(incident);
    }

    /**
     * Grants an AWAITING_APPROVAL incident and issues the remediation command.
     * The incident moves AUTO_APPROVED → REMEDIATING immediately after the
     * command is on {@code obs.remediation} (Phase 6 Unit 3); the result
     * consumer advances it to VERIFYING or FAILED later.
     */
    @Transactional
    public Incident approve(String incidentId, OperatorActor subject) {
        Incident incident = repository.findByIncidentId(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
        if (!IncidentStatus.AWAITING_APPROVAL.name().equals(incident.getStatus())) {
            throw new ApprovalNotAllowedException(
                    "Incident " + incidentId + " is " + incident.getStatus()
                            + "; only AWAITING_APPROVAL incidents can be approved");
        }
        approveAndIssue(incident, subject);
        incident.markUpdated();
        return repository.save(incident);
    }

    private void approveAndIssue(Incident incident, OperatorActor subject) {
        incident.setStatus(IncidentStatus.AUTO_APPROVED.name());
        incident.setApprovedAt(Instant.now());
        incident.setApprovedBy(subject.name());
        UUID commandId = UUID.randomUUID();
        RemediationCommand command = new RemediationCommand(
                commandId,
                incident.getIncidentId(),
                RemediationAction.ROLLBACK,
                incident.getServiceId(),
                Env.valueOf(incident.getEnv()),
                null,
                Instant.now(),
                incident.getApprovedBy());
        remediationCommandKafkaTemplate.send(Topics.REMEDIATION, commandId.toString(), command);
        incident.setRemediationCommandId(commandId.toString());
        incident.setStatus(IncidentStatus.REMEDIATING.name());
    }

    public Map<String, Object> toSummary(Incident i) {
        Map<String, Object> m = new HashMap<>();
        m.put("incidentId", i.getIncidentId());
        m.put("serviceId", i.getServiceId());
        m.put("env", i.getEnv());
        m.put("severity", i.getSeverity());
        m.put("status", i.getStatus());
        m.put("title", i.getTitle());
        m.put("description", i.getDescription());
        m.put("errorRate", i.getErrorRate());
        m.put("errorCount", i.getErrorCount());
        m.put("windowStart", i.getWindowStart() != null ? i.getWindowStart().toString() : null);
        m.put("windowEnd", i.getWindowEnd() != null ? i.getWindowEnd().toString() : null);
        m.put("detectedAt", i.getDetectedAt() != null ? i.getDetectedAt().toString() : null);
        m.put("resolvedAt", i.getResolvedAt() != null ? i.getResolvedAt().toString() : null);
        m.put("investigatedAt", i.getInvestigatedAt() != null ? i.getInvestigatedAt().toString() : null);
        m.put("policyDecision", i.getPolicyDecision());
        m.put("requestedAt", i.getRequestedAt() != null ? i.getRequestedAt().toString() : null);
        m.put("approvedAt", i.getApprovedAt() != null ? i.getApprovedAt().toString() : null);
        m.put("approvedBy", i.getApprovedBy());
        m.put("remediationCommandId", i.getRemediationCommandId());
        m.put("remediationOutcome", i.getRemediationOutcome());
        m.put("remediationCompletedAt", i.getRemediationCompletedAt() != null
                ? i.getRemediationCompletedAt().toString() : null);
        m.put("metadata", safeJson(i.getMetadata()));
        m.put("hypothesis", safeJson(i.getHypothesis()));
        return m;
    }

    private Object safeJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // Plain Map/List/String (not a Jackson-2 JsonNode) so Spring's
            // Jackson-3 converter serializes it as a JSON tree, not a bean.
            return Json.mapper().readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }
}