package io.vykronis.gateway.auth;

import org.springframework.web.server.ServerWebExchange;

/**
 * Default identity provider: auth is disabled, every caller is anonymous.
 * Replaced by the OIDC provider when {@code vykronis.auth.enabled=true}
 * (Phase 5 Unit 2). Decided as a bean so the identity seam always has exactly
 * one active provider.
 */
public class AnonymousIdentityProvider implements IdentityProvider {

    @Override
    public Identity identityOf(ServerWebExchange exchange) {
        return Identity.ANONYMOUS;
    }
}