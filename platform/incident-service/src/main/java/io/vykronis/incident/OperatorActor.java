package io.vykronis.incident;

import java.util.Set;

/**
 * The human or service that acted on an incident (remediation request, approval).
 * Shape mirrors the gateway {@code Identity} and the policy service's
 * {@code PolicySubject} so an actor can be forwarded for a policy evaluation
 * without a shared type.
 */
public record OperatorActor(String name, Set<String> roles, boolean service) {

    public OperatorActor {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public static OperatorActor user(String name, String... roles) {
        return new OperatorActor(name, Set.of(roles), false);
    }

    public static OperatorActor service(String name) {
        return new OperatorActor(name, Set.of(), true);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
