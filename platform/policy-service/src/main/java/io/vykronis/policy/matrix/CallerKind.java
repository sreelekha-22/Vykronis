package io.vykronis.policy.matrix;

import io.vykronis.policy.PolicySubject;

import java.util.function.Predicate;

/**
 * The kinds of remediation caller recognised by the decision matrix. The
 * predicates partition every {@link PolicySubject} into exactly one kind, so
 * a matrix row for (action, env, caller) is unambiguous.
 */
public enum CallerKind {

    /** A machine/service account, never allowed to remediate critical envs. */
    SERVICE(s -> s.service()),

    /** A human who does not hold the approver role. */
    HUMAN(s -> !s.service() && !s.hasRole(DecisionMatrix.APPROVER_ROLE)),

    /** A human who holds the approver role. */
    HUMAN_APPROVER(s -> !s.service() && s.hasRole(DecisionMatrix.APPROVER_ROLE));

    private final Predicate<PolicySubject> predicate;

    CallerKind(Predicate<PolicySubject> predicate) {
        this.predicate = predicate;
    }

    public boolean matches(PolicySubject subject) {
        return predicate.test(subject);
    }

    /**
     * A representative subject for this caller kind, used to drive the matrix
     * table from tests (single source of truth).
     */
    public PolicySubject exampleSubject() {
        return switch (this) {
            case SERVICE -> PolicySubject.service("service-account-vykronis-cli");
            case HUMAN -> PolicySubject.user("alice", "vykronis-user");
            case HUMAN_APPROVER ->
                    PolicySubject.user("ops", "vykronis-user", DecisionMatrix.APPROVER_ROLE);
        };
    }
}