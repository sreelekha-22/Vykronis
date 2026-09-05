package io.vykronis.policy.web;

import io.vykronis.policy.EvaluationResult;
import io.vykronis.policy.PolicyService;
import io.vykronis.policy.audit.AuditEntry;
import io.vykronis.policy.audit.AuditLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Policy decision + append-only audit endpoints. Decisions are recorded before
 * they are returned so every evaluation is verifiable against the audit log.
 */
@RestController
@RequestMapping("/api/policy")
public class PolicyController {

    private final PolicyService policyService;
    private final AuditLog auditLog;

    public PolicyController(PolicyService policyService, AuditLog auditLog) {
        this.policyService = policyService;
        this.auditLog = auditLog;
    }

    @PostMapping("/evaluate")
    public EvaluationResponse evaluate(@RequestBody EvaluationRequest request) {
        if (request.action() == null || request.environment() == null || request.subject() == null) {
            throw new IllegalArgumentException("action, environment and subject are required");
        }
        EvaluationResult result = policyService.evaluate(request.action(), request.environment(), request.subject());
        AuditEntry entry = result.auditEntry();
        return new EvaluationResponse(
                request.incidentId(), request.action(), request.environment(),
                result.decision(), result.reason(), entry.sequence(), entry.decidedAt());
    }

    @GetMapping("/audit")
    public List<AuditEntry> audit(@RequestParam(defaultValue = "50") int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<AuditEntry> all = auditLog.entries();
        int from = Math.max(0, all.size() - limit);
        return List.copyOf(all.subList(from, all.size()));
    }
}