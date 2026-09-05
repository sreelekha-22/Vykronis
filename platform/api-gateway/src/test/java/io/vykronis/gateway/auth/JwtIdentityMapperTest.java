package io.vykronis.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIdentityMapperTest {

    private final JwtIdentityMapper mapper = new JwtIdentityMapper();

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256", "kid", "k1"), claims);
    }

    @Test
    void mapsHumanTokenClaims() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-1",
                "preferred_username", "alice",
                "realm_access", Map.of("roles", List.of("vykronis-user", "offline_access")),
                "scope", "profile email",
                "azp", "vykronis-console"));

        Identity identity = mapper.map(jwt);

        assertThat(identity.subject()).isEqualTo("user-1");
        assertThat(identity.name()).isEqualTo("alice");
        assertThat(identity.roles())
                .containsExactlyInAnyOrder("vykronis-user", "offline_access");
        assertThat(identity.scopes()).containsExactlyInAnyOrder("profile", "email");
        assertThat(identity.service()).isFalse();
        assertThat(identity.hasRole("vykronis-user")).isTrue();
        assertThat(identity.hasScope("email")).isTrue();
    }

    @Test
    void mapsServiceAccountTokenFromResourceAccessOfTheCallingClient() {
        Jwt jwt = jwt(Map.of(
                "sub", "svc-1",
                "preferred_username", "service-account-vykronis-cli",
                "azp", "vykronis-cli",
                "resource_access", Map.of(
                        "vykronis-cli", Map.of("roles", List.of("remediation-executor"))),
                "realm_access", Map.of("roles", List.of("offline_access"))));

        Identity identity = mapper.map(jwt);

        assertThat(identity.service()).isTrue();
        assertThat(identity.name()).isEqualTo("service-account-vykronis-cli");
        assertThat(identity.roles()).containsExactlyInAnyOrder("remediation-executor");
        assertThat(identity.hasRole("remediation-executor")).isTrue();
    }

    @Test
    void toleratesMissingClaims() {
        Jwt jwt = jwt(Map.of("sub", "someone"));

        Identity identity = mapper.map(jwt);

        assertThat(identity.subject()).isEqualTo("someone");
        assertThat(identity.roles()).isEmpty();
        assertThat(identity.scopes()).isEmpty();
        assertThat(identity.service()).isFalse();
    }
}