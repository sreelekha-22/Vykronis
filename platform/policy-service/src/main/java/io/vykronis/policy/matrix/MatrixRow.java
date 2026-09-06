package io.vykronis.policy.matrix;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;

/**
 * One declarative row of the environment-based decision matrix: the decision
 * granted for {@code action} in {@code environment} to {@code caller}. The
 * standard table is exhaustive (every action, env and caller kind has exactly
 * one row) so it is the single source of truth for remediation policy.
 *
 * @param action      governed action
 * @param environment governed environment
 * @param caller      kind of caller the row applies to
 * @param decision    the decision for matching callers (fail-closed {@code DENY}
 *                    when the table has no row — but the standard table covers all)
 */
public record MatrixRow(
        PolicyAction action,
        Env environment,
        CallerKind caller,
        PolicyDecision decision) {

    @Override
    public String toString() {
        return action + " " + environment + " " + caller + " -> " + decision;
    }
}