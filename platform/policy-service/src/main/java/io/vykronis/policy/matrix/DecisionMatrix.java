package io.vykronis.policy.matrix;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;
import io.vykronis.policy.PolicySubject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The environment-based decision matrix: the single source of truth for
 * remediation policy is the declarative {@link #standardTable()} — every
 * (action, env, caller) combination is spelled out, and {@link #standard()}
 * is built from it so the runtime can never drift from the table. Rules are
 * evaluated in order; the first row whose action/environment/match predicate
 * wins. Anything unmatched is {@link PolicyDecision#DENY} (fail closed).
 *
 * <p>Phase 5 done-when behaviour: rollback runs automatically in DEV and
 * needs a human approver's click in PROD; service accounts never remediate
 * PROD.
 */
public final class DecisionMatrix {

    public static final String APPROVER_ROLE = "vykronis-approver";

    private final List<Rule> rules;

    public DecisionMatrix(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * The standard production table, spelled out row by row. Every
     * {@code action} × {@code environment} × {@code caller} combination has
     * exactly one row, so the table is complete and the tests can drive the
     * matrix straight from it.
     */
    public static List<MatrixRow> standardTable() {
        return List.of(
                // ROLLBACK — remediating a running system
                row(PolicyAction.ROLLBACK, Env.DEV, CallerKind.SERVICE, PolicyDecision.DENY),
                row(PolicyAction.ROLLBACK, Env.DEV, CallerKind.HUMAN, PolicyDecision.ALLOW),
                row(PolicyAction.ROLLBACK, Env.DEV, CallerKind.HUMAN_APPROVER, PolicyDecision.ALLOW),
                row(PolicyAction.ROLLBACK, Env.PROD, CallerKind.SERVICE, PolicyDecision.DENY),
                row(PolicyAction.ROLLBACK, Env.PROD, CallerKind.HUMAN, PolicyDecision.DENY),
                row(PolicyAction.ROLLBACK, Env.PROD, CallerKind.HUMAN_APPROVER, PolicyDecision.REQUIRE_APPROVAL),

                // RESTART — same shape as rollback
                row(PolicyAction.RESTART, Env.DEV, CallerKind.SERVICE, PolicyDecision.DENY),
                row(PolicyAction.RESTART, Env.DEV, CallerKind.HUMAN, PolicyDecision.ALLOW),
                row(PolicyAction.RESTART, Env.DEV, CallerKind.HUMAN_APPROVER, PolicyDecision.ALLOW),
                row(PolicyAction.RESTART, Env.PROD, CallerKind.SERVICE, PolicyDecision.DENY),
                row(PolicyAction.RESTART, Env.PROD, CallerKind.HUMAN, PolicyDecision.DENY),
                row(PolicyAction.RESTART, Env.PROD, CallerKind.HUMAN_APPROVER, PolicyDecision.REQUIRE_APPROVAL)
        );
    }

    private static MatrixRow row(
            PolicyAction action, Env environment, CallerKind caller, PolicyDecision decision) {
        return new MatrixRow(action, environment, caller, decision);
    }

    /**
     * The standard production matrix, built from {@link #standardTable()}.
     */
    public static DecisionMatrix standard() {
        return from(standardTable());
    }

    /**
     * Builds a matrix from a declarative table. Rows are converted to the
     * predicate rules in table order; the caller-kinds partition every
     * subject, so each (action, env, caller) touches exactly one row.
     */
    public static DecisionMatrix from(List<MatrixRow> table) {
        List<Rule> rules = new ArrayList<>();
        for (MatrixRow row : table) {
            rules.add(new Rule(row.action(), row.environment(), row.caller()::matches, row.decision()));
        }
        return new DecisionMatrix(rules);
    }

    public PolicyDecision decide(PolicyAction action, Env environment, PolicySubject subject) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(subject, "subject");
        for (Rule rule : rules) {
            if (rule.action() == action
                    && rule.environment() == environment
                    && rule.who().test(subject)) {
                return rule.decision();
            }
        }
        return PolicyDecision.DENY;
    }

    public List<Rule> rules() {
        return rules;
    }
}