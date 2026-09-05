package io.vykronis.gateway.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Application-facing facade over {@link IdentityProvider}: the only way gateway
 * components obtain the caller's {@link Identity}.
 */
@Component
public class CurrentIdentity {

    private final IdentityProvider provider;

    public CurrentIdentity(IdentityProvider provider) {
        this.provider = provider;
    }

    public Identity identityOf(ServerWebExchange exchange) {
        return provider.identityOf(exchange);
    }
}