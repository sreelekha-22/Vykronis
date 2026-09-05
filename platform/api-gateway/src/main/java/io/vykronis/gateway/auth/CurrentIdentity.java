package io.vykronis.gateway.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Application-facing facade over identity resolution: the only way gateway
 * components obtain the caller's {@link Identity}. Returns the identity
 * resolved by {@link AuthenticationFilter} when auth is on; otherwise falls
 * back to the configured {@link IdentityProvider} (anonymous by default).
 */
@Component
public class CurrentIdentity {

    private final IdentityProvider provider;

    public CurrentIdentity(IdentityProvider provider) {
        this.provider = provider;
    }

    public Identity identityOf(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(GatewayAttributes.IDENTITY);
        if (attribute instanceof Identity identity) {
            return identity;
        }
        return provider.identityOf(exchange);
    }
}