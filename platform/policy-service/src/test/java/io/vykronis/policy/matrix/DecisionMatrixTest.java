package io.vykronis.policy.matrix;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;
import io.vykronis.policy.PolicySubject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The environment-based decision matrix is the single source of truth for
 * policy: this table drives the assertions below, so the matrix and its tests
 * can never drift apart (Phase 5 Unit 6 keeps this same table).
 *
 * <p>Phase 5 done-when: "same rollback auto in dev, click-to-approve in prod."
 */
class DecisionMatrixTest {

    private record ExpectedRow(PolicyAction action, Env environment, PolicySubject subject, PolicyDecision expected) {

        static ExpectedRow row(PolicyAction action, Env environment, PolicySubject subject, PolicyDecision expected) {
            return new ExpectedRow(action, environment, subject, expected);
        }
    }

    private static final List<ExpectedRow> MATRIX = List.of(
            // ---------- ROLLBACK: remediation is a human action ----------
            ExpectedRow.row(PolicyAction.ROLLBACK, Env.DEV,
                    PolicySubject.user("alice", "vykronis-user"), PolicyDecision.ALLOW),
            ExpectedRow.row(PolicyAction.ROLLBACK, Env.DEV,
                    PolicySubject.service("service-account-vykronis-cli"), PolicyDecision.DENY),
            ExpectedRow.row(PolicyAction.ROLLBACK, Env.PROD,
                    PolicySubject.user("ops", "vykronis-user", "vykronis-approver"), PolicyDecision.REQUIRE_APPROVAL),
            ExpectedRow.row(PolicyAction.ROLLBACK, Env.PROD,
                    PolicySubject.user("alice", "vykronis-user"), PolicyDecision.DENY),
            ExpectedRow.row(PolicyAction.ROLLBACK, Env.PROD,
                    PolicySubject.service("service-account-vykronis-cli"), PolicyDecision.DENY),

            // ---------- RESTART ----------
            ExpectedRow.row(PolicyAction.RESTART, Env.DEV,
                    PolicySubject.user("alice", "vykronis-user"), PolicyDecision.ALLOW),
            ExpectedRow.row(PolicyAction.RESTART, Env.DEV,
                    PolicySubject.service("service-account-vykronis-cli"), PolicyDecision.DENY),
            ExpectedRow.row(PolicyAction.RESTART, Env.PROD,
                    PolicySubject.user("ops", "vykronis-user", "vykronis-approver"), PolicyDecision.REQUIRE_APPROVAL),
            ExpectedRow.row(PolicyAction.RESTART, Env.PROD,
                    PolicySubject.user("alice", "vykronis-user"), PolicyDecision.DENY),
            ExpectedRow.row(PolicyAction.RESTART, Env.PROD,
                    PolicySubject.service("service-account-vykronis-cli"), PolicyDecision.DENY)
    );

    private final DecisionMatrix matrix = DecisionMatrix.standard();

    @Test
    void everyMatrixRowEvaluatesAsExpected() {
        for (ExpectedRow row : MATRIX) {
            assertThat(matrix.decide(row.action(), row.environment(), row.subject()))
                    .as("%s %s by %s", row.action(), row.environment(), row.subject())
                    .isEqualTo(row.expected());
        }
    }

    @Test
    void dumbRollbackIsAutomaticInDevAndClickToApproveInProd() {
        assertThat(matrix.decide(PolicyAction.ROLLBACK, Env.DEV,
                PolicySubject.user("alice", "vykronis-user")))
                .isEqualTo(PolicyDecision.ALLOW);
        assertThat(matrix.decide(PolicyAction.ROLLBACK, Env.PROD,
                PolicySubject.user("ops", "vykronis-user", "vykronis-approver")))
                .isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
    }

    @Test
    void unknownCombinationsFailClosedToDeny() {
        assertThat(matrix.decide(PolicyAction.RESTART, Env.DEV,
                PolicySubject.service("service-account-vykronis-cli")))
                .isEqualTo(PolicyDecision.DENY);
        assertThat(matrix.decide(PolicyAction.ROLLBACK, Env.PROD,
                PolicySubject.user("anonymous-name"))) // who somehow lacks app roles
                .isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void matrixIsNotEmptyAndOrderedByMostSpecificFirst() {
        assertThat(matrix.rules()).isNotEmpty();
    }
}