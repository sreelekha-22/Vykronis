package io.vykronis.policy;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.audit.AuditEntry;
import io.vykronis.policy.audit.AuditLog;
import io.vykronis.policy.matrix.DecisionMatrix;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyServiceTest {

    private final AuditLog auditLog = mock(AuditLog.class);
    private final PolicyService service = new PolicyService(DecisionMatrix.standard(), auditLog);

    private static AuditEntry entry(long sequence) {
        return AuditEntry.of(sequence, Instant.now(),
                PolicyAction.ROLLBACK, Env.PROD,
                PolicySubject.user("ops", "vykronis-user", "vykronis-approver"),
                PolicyDecision.REQUIRE_APPROVAL, "0");
    }

    @Test
    void recordsRollbackInProdByApproverAndReturnsRequireApproval() {
        PolicySubject ops = PolicySubject.user("ops", "vykronis-user", "vykronis-approver");
        when(auditLog.append(any(), any(), any(), any())).thenReturn(entry(7L));

        EvaluationResult result = service.evaluate(PolicyAction.ROLLBACK, Env.PROD, ops);

        assertThat(result.decision()).isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
        assertThat(result.reason()).contains("approval");
        assertThat(result.auditEntry().sequence()).isEqualTo(7L);
        verify(auditLog).append(eq(PolicyAction.ROLLBACK), eq(Env.PROD), eq(ops), eq(PolicyDecision.REQUIRE_APPROVAL));
    }

    @Test
    void rollbackInDevRunsAutomatically() {
        PolicySubject alice = PolicySubject.user("alice", "vykronis-user");
        when(auditLog.append(any(), any(), any(), any())).thenReturn(entry(8L));

        EvaluationResult result = service.evaluate(PolicyAction.ROLLBACK, Env.DEV, alice);

        assertThat(result.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(result.reason()).contains("automatic");
    }

    @Test
    void nonApproverRollingBackProdIsDeniedFailsClosed() {
        PolicySubject mallory = PolicySubject.user("mallory", "vykronis-user");
        when(auditLog.append(any(), any(), any(), any())).thenReturn(entry(9L));

        EvaluationResult result = service.evaluate(PolicyAction.ROLLBACK, Env.PROD, mallory);

        assertThat(result.decision()).isEqualTo(PolicyDecision.DENY);
    }
}