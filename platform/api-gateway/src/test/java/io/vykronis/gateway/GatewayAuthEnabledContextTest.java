package io.vykronis.gateway;

import io.vykronis.gateway.auth.AuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "vykronis.auth.enabled=true",
        "vykronis.auth.jwk-set-uri=http://localhost:8088/realms/vykronis/protocol/openid-connect/certs",
        "vykronis.auth.issuer-uri=http://localhost:8088/realms/vykronis"
})
class GatewayAuthEnabledContextTest {

    @Autowired
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired
    private AuthenticationFilter authenticationFilter;

    @Test
    void wiresJwtDecoderWhenAuthEnabled() {
        assertThat(reactiveJwtDecoder).isNotNull();
    }

    @Test
    void wiresAuthenticationFilterWhenAuthEnabled() {
        assertThat(authenticationFilter).isNotNull();
    }
}