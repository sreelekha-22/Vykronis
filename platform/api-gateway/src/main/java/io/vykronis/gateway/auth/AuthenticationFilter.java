package io.vykronis.gateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * OIDC resource-server behaviour at the gateway edge.
 *
 * <p>When auth is disabled (default) every request passes through anonymous.
 * When enabled ({@code vykronis.auth.enabled=true}) a callers must present a
 * valid {@code Bearer} JWT: decoded against the Keycloak JWKS, mapped to
 * {@link Identity} and published as the {@link GatewayAttributes#IDENTITY}
 * exchange attribute; anything else is rejected with 401 JSON. Role/scoping
 * decisions happen downstream through {@link CurrentIdentity} (Phase 5 Unit 3).
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final boolean authEnabled;
    private final ReactiveJwtDecoder jwtDecoder;
    private final JwtIdentityMapper jwtIdentityMapper;

    public AuthenticationFilter(
            @Value("${vykronis.auth.enabled:false}") boolean authEnabled,
            @Autowired(required = false) ReactiveJwtDecoder jwtDecoder,
            JwtIdentityMapper jwtIdentityMapper) {
        if (authEnabled && jwtDecoder == null) {
            throw new IllegalStateException(
                    "vykronis.auth.enabled=true requires a ReactiveJwtDecoder bean "
                            + "(check vykronis.auth.jwk-set-uri / vykronis.auth.issuer-uri)");
        }
        this.authEnabled = authEnabled;
        this.jwtDecoder = jwtDecoder;
        this.jwtIdentityMapper = jwtIdentityMapper;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!authEnabled) {
            return chain.filter(exchange);
        }
        String token = bearerToken(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            log.warn("rejected anonymous request {}: missing bearer token", exchange.getRequest().getPath());
            return writeUnauthorized(exchange, "missing or malformed bearer token");
        }
        return jwtDecoder.decode(token)
                .map(jwt -> {
                    Identity identity = jwtIdentityMapper.map(jwt);
                    exchange.getAttributes().put(GatewayAttributes.IDENTITY, identity);
                    if (log.isDebugEnabled()) {
                        log.debug("authenticated {} ({}) service={}",
                                identity.subject(), identity.name(), identity.service());
                    }
                    return exchange;
                })
                .flatMap(chain::filter)
                .onErrorResume(JwtException.class, e -> {
                    log.warn("rejected request {}: {}", exchange.getRequest().getPath(), e.getMessage());
                    return writeUnauthorized(exchange, "invalid bearer token");
                });
    }

    private String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String trimmed = authorization.trim();
        if (!trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"unauthorized\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}