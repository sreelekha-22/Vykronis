package io.vykronis.policy.matrix;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;
import io.vykronis.policy.PolicySubject;

import java.util.function.Predicate;

/**
 * One row of the decision matrix: a decision for {@code action} in
 * {@code environment} when {@code who} matches the caller.
 *
 * @param action     governed action
 * @param environment governed environment
 * @param who        predicate deciding which callers this row applies to
 * @param decision   the decision for matching callers
 */
public record Rule(
        PolicyAction action,
        Env environment,
        Predicate<PolicySubject> who,
        PolicyDecision decision) {

    @Override
    public String toString() {
        return "Rule[" + action + " " + environment + " -> " + decision + "]";
    }
}