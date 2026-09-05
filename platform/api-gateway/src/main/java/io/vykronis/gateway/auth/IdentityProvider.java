package io.vykronis.gateway.auth;

import org.springframework.web.server.ServerWebExchange;

/**
 * Dedicated seam between gateway route/authorization logic and identity
 * sources. Implementation today: {@link AnonymousIdentityProvider} (auth off,
 * Phase 5 Unit 1 default); the Keycloak/OIDC resource server (Phase 5 Unit 2)
 * provides a real one. Tests mock this interface instead of real tokens.
 */
public interface IdentityProvider {

    /**
     * Resolves the identity of {@code exchange}. Must not return {@code null}:
     * unauthenticated callers are {@link Identity#ANONYMOUS}.
     */
    Identity identityOf(ServerWebExchange exchange);
}