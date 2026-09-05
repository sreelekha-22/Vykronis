package io.vykronis.policy;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.audit.AuditEntry;
import io.vykronis.policy.audit.AuditLog;
import io.vykronis.policy.matrix.DecisionMatrix;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Evaluates remediation requests against the env-based decision matrix and
 * records every decision (append-only) in the audit log.
 */
@Service
public class PolicyService {

    private final DecisionMatrix matrix;
    private final AuditLog auditLog;

    public PolicyService(DecisionMatrix matrix, AuditLog auditLog) {
        this.matrix = matrix;
        this.auditLog = auditLog;
    }

    public EvaluationResult evaluate(PolicyAction action, Env environment, PolicySubject subject) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(subject, "subject");
        PolicyDecision decision = matrix.decide(action, environment, subject);
        AuditEntry auditEntry = auditLog.append(action, environment, subject, decision);
        return new EvaluationResult(decision, reason(action, environment, decision), auditEntry);
    }

    private String reason(PolicyAction action, Env environment, PolicyDecision decision) {
        return switch (decision) {
            case ALLOW -> action + " in " + environment + " runs automatically (policy ALLOW)";
            case REQUIRE_APPROVAL -> action + " in " + environment + " requires approval (policy REQUIRE_APPROVAL)";
            case DENY -> action + " in " + environment + " is not permitted (policy DENY)";
        };
    }
}