package io.vykronis.gateway.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps Keycloak access-token claims onto {@link Identity}. User tokens carry
 * realm roles in {@code realm_access.roles}; service-account tokens (client
 * credentials, {@code preferred_username} starts with {@code service-account-})
 * carry client roles in {@code resource_access.<azp>.roles}.
 */
@Component
public class JwtIdentityMapper {

    private static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

    public Identity map(Jwt jwt) {
        Map<String, Object> claims = jwt.getClaims();
        String subject = string(claims.get("sub"), "");
        String preferredUsername = string(claims.get("preferred_username"), null);
        boolean service = preferredUsername != null && preferredUsername.startsWith(SERVICE_ACCOUNT_PREFIX);
        String name = preferredUsername != null
                ? preferredUsername
                : string(claims.get("name"), subject);
        return new Identity(subject, name, rolesOf(claims, service), scopesOf(claims), service);
    }

    private Set<String> rolesOf(Map<String, Object> claims, boolean service) {
        if (service) {
            Map<String, Object> callingClient = clientAccess(claims);
            List<?> clientRoles = nestedList(callingClient, "roles");
            if (!clientRoles.isEmpty()) {
                return toStringSet(clientRoles);
            }
        }
        return toStringSet(nestedList(claims.get("realm_access"), "roles"));
    }

    private Map<String, Object> clientAccess(Map<String, Object> claims) {
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceFile) {
            Object client = resourceFile.get(string(claims.get("azp"), ""));
            if (client instanceof Map<?, ?> clientFile) {
                return clientFile.entrySet().stream()
                        .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue));
            }
        }
        return Map.of();
    }

    private Set<String> scopesOf(Map<String, Object> claims) {
        String scope = string(claims.get("scope"), "");
        return Arrays.stream(scope.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private List<?> nestedList(Object container, String key) {
        if (container instanceof Map<?, ?> m) {
            Object value = m.get(key);
            if (value instanceof List<?> list) {
                return list;
            }
        }
        return List.of();
    }

    private Set<String> toStringSet(List<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.toSet());
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}