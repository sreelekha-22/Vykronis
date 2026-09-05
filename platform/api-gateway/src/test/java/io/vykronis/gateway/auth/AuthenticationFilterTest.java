package io.vykronis.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {

    private static final String ALICE_TOKEN = "alice-token";

    private final ReactiveJwtDecoder decoder = mock(ReactiveJwtDecoder.class);

    private AuthenticationFilter filter(boolean enabled) {
        return new AuthenticationFilter(enabled, decoder, new JwtIdentityMapper());
    }

    private Jwt aliceJwt() {
        return new Jwt(ALICE_TOKEN, Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "user-1",
                        "preferred_username", "alice",
                        "realm_access", Map.of("roles", List.of("vykronis-user")),
                        "scope", "profile email"));
    }

    @Test
    void rejectsAnonymousCallerWhenAuthRequired() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/incidents").build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        StepVerifier.create(filter(true).filter(exchange, chain(chainInvoked)))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainInvoked.get()).isFalse();
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("error", "unauthorized");
    }

    @Test
    void rejectsInvalidTokenWhenAuthRequired() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage")
                        .build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        when(decoder.decode(anyString())).thenReturn(Mono.error(new JwtException("bad signature")));

        StepVerifier.create(filter(true).filter(exchange, chain(chainInvoked)))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainInvoked.get()).isFalse();
    }

    @Test
    void allowsValidBearerAndResolvesIdentityAttribute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ALICE_TOKEN)
                        .build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        when(decoder.decode(ALICE_TOKEN)).thenReturn(Mono.just(aliceJwt()));

        StepVerifier.create(filter(true).filter(exchange, chain(chainInvoked)))
                .verifyComplete();

        assertThat(chainInvoked.get()).isTrue();
        Identity identity = exchange.getAttribute(GatewayAttributes.IDENTITY);
        assertThat(identity).isNotNull();
        assertThat(identity.subject()).isEqualTo("user-1");
        assertThat(identity.name()).isEqualTo("alice");
        assertThat(identity.hasRole("vykronis-user")).isTrue();
    }

    @Test
    void passesThroughWhenAuthDisabled() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/incidents").build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        StepVerifier.create(filter(false).filter(exchange, chain(chainInvoked)))
                .verifyComplete();

        assertThat(chainInvoked.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((Object) exchange.getAttribute(GatewayAttributes.IDENTITY)).isNull();
    }

    private GatewayFilterChain chain(AtomicBoolean invoked) {
        return exchange -> {
            invoked.set(true);
            return Mono.empty();
        };
    }
}