package io.vykronis.policy;

import java.util.Set;

/**
 * The caller a policy decision is made for. Shape mirrors the gateway's
 * {@code Identity} (roles + service flag) so the gateway can forward
 * evaluations without a shared type.
 */
public record PolicySubject(String name, Set<String> roles, boolean service) {

    public PolicySubject {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public static PolicySubject user(String name, String... roles) {
        return new PolicySubject(name, Set.of(roles), false);
    }

    public static PolicySubject service(String name) {
        return new PolicySubject(name, Set.of(), true);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}