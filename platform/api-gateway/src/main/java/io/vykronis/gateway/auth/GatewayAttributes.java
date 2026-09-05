package io.vykronis.gateway.auth;

import org.springframework.web.server.ServerWebExchange;

/**
 * Keys exchanged in the gateway's {@link ServerWebExchange} attributes.
 */
public final class GatewayAttributes {

    /** The resolved {@link Identity} of the caller (set by {@link AuthenticationFilter}). */
    public static final String IDENTITY = GatewayAttributes.class.getName() + ".IDENTITY";

    private GatewayAttributes() {
    }
}