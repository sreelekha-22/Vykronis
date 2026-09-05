package io.vykronis.policy.matrix;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;
import io.vykronis.policy.PolicySubject;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The environment-based decision matrix: the single source of truth for
 * remediation policy. Rules are evaluated in order; the first row whose
 * action/environment/match predicate hits wins. Anything unmatched is
 * {@link PolicyDecision#DENY} (fail closed).
 *
 * <p>Phase 5 done-when behaviour: rollback runs automatically in DEV and
 * needs a human approver's click in PROD; services never remediate PROD.
 */
public final class DecisionMatrix {

    public static final String APPROVER_ROLE = "vykronis-approver";

    private final List<Rule> rules;

    public DecisionMatrix(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * Standard production matrix. Order matters: the most specific rows come
     * first so a caller can never match a broader rule by accident.
     */
    public static DecisionMatrix standard() {
        Predicate<PolicySubject> human =
                s -> !s.service();
        Predicate<PolicySubject> humanWithoutApprover =
                s -> !s.service() && !s.hasRole(APPROVER_ROLE);
        Predicate<PolicySubject> humanApprover =
                s -> !s.service() && s.hasRole(APPROVER_ROLE);

        List<Rule> rules = List.of(
                new Rule(PolicyAction.ROLLBACK, Env.PROD, PolicySubject::service, PolicyDecision.DENY),
                new Rule(PolicyAction.ROLLBACK, Env.PROD, humanWithoutApprover, PolicyDecision.DENY),
                new Rule(PolicyAction.ROLLBACK, Env.PROD, humanApprover, PolicyDecision.REQUIRE_APPROVAL),
                new Rule(PolicyAction.ROLLBACK, Env.DEV, human, PolicyDecision.ALLOW),

                new Rule(PolicyAction.RESTART, Env.PROD, PolicySubject::service, PolicyDecision.DENY),
                new Rule(PolicyAction.RESTART, Env.PROD, humanWithoutApprover, PolicyDecision.DENY),
                new Rule(PolicyAction.RESTART, Env.PROD, humanApprover, PolicyDecision.REQUIRE_APPROVAL),
                new Rule(PolicyAction.RESTART, Env.DEV, human, PolicyDecision.ALLOW)
        );
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