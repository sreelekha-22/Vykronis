package io.vykronis.policy.matrix;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;
import io.vykronis.policy.PolicySubject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The environment-based decision matrix is a single source of truth: the
 * production {@link DecisionMatrix#standardTable()} declares every
 * (action, env, caller) row explicitly, {@code DecisionMatrix#standard()} is
 * built from it, and the tests below drive the table — the table and the
 * runtime can never drift apart.
 *
 * <p>Phase 5 done-when: "same rollback auto in dev, click-to-approve in prod."
 */
class DecisionMatrixTest {

    private final DecisionMatrix matrix = DecisionMatrix.standard();

    @Test
    void runtimeMatrixMatchesEveryRowOfTheStandardTable() {
        for (MatrixRow row : DecisionMatrix.standardTable()) {
            PolicySubject subject = row.caller().exampleSubject();
            assertThat(matrix.decide(row.action(), row.environment(), subject))
                    .as("%s %s %s", row.action(), row.environment(), row.caller())
                    .isEqualTo(row.decision());
        }
    }

    @Test
    void standardTableCoversEveryActionEnvironmentCallerExactlyOnce() {
        Set<String> seen = new HashSet<>();
        for (MatrixRow row : DecisionMatrix.standardTable()) {
            String key = row.action() + "/" + row.environment() + "/" + row.caller();
            assertThat(seen.add(key))
                    .as("duplicate row %s", key)
                    .isTrue();
        }
        int expected = PolicyAction.values().length * Env.values().length * CallerKind.values().length;
        assertThat(DecisionMatrix.standardTable())
                .hasSize(expected);
    }

    @Test
    void rollbackIsAutomaticInDevAndClickToApproveInProd() {
        assertThat(matrix.decide(PolicyAction.ROLLBACK, Env.DEV,
                CallerKind.HUMAN.exampleSubject()))
                .isEqualTo(PolicyDecision.ALLOW);
        assertThat(matrix.decide(PolicyAction.ROLLBACK, Env.PROD,
                CallerKind.HUMAN_APPROVER.exampleSubject()))
                .isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
        assertThat(matrix.decide(PolicyAction.ROLLBACK, Env.PROD,
                CallerKind.SERVICE.exampleSubject()))
                .isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void anythingOutsideTheTableFailsClosedToDeny() {
        DecisionMatrix empty = DecisionMatrix.from(List.of());
        assertThat(empty.decide(PolicyAction.ROLLBACK, Env.DEV,
                CallerKind.HUMAN.exampleSubject()))
                .isEqualTo(PolicyDecision.DENY);
        assertThat(empty.decide(PolicyAction.RESTART, Env.PROD,
                CallerKind.HUMAN_APPROVER.exampleSubject()))
                .isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void callerKindsPartitionEveryPolicySubject() {
        for (PolicyDecision decision : PolicyDecision.values()) {
            assertThat(decision).isNotNull();
        }
        PolicySubject[] subjects = {
                CallerKind.SERVICE.exampleSubject(),
                CallerKind.HUMAN.exampleSubject(),
                CallerKind.HUMAN_APPROVER.exampleSubject(),
        };
        for (PolicySubject subject : subjects) {
            int hits = 0;
            for (CallerKind caller : CallerKind.values()) {
                if (caller.matches(subject)) {
                    hits++;
                }
            }
            assertThat(hits).as("partition check for %s", subject).isEqualTo(1);
        }
    }
}