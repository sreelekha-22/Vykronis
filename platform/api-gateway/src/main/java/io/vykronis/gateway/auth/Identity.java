package io.vykronis.gateway.auth;

import java.util.Set;

/**
 * The authenticated caller of an inbound gateway request. The gateway decides
 * by ROLE/SCOPE membership, never by touching raw JWT claims.
 *
 * @param subject stable identity (subject claim / username)
 * @param name    display name
 * @param roles   realm/application roles, e.g. {@code vykronis-approver}
 * @param scopes  granted OAuth2 scopes, e.g. {@code remediation.execute}
 * @param service {@code true} for a machine identity (agent/remediation), users are {@code false}
 */
public record Identity(String subject, String name, Set<String> roles, Set<String> scopes, boolean service) {

    public static final Identity ANONYMOUS =
            new Identity("anonymous", "Anonymous", Set.of(), Set.of(), false);

    public Identity {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}