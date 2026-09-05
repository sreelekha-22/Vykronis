package io.vykronis.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The identity seam: gateway code resolves the caller through
 * {@link IdentityProvider}, never from raw headers/JWT internals directly.
 * Tests mock the provider, and the default (auth off) resolves the anonymous
 * identity — the OIDC resource server (Unit 2) swaps the default with a
 * Keycloak-backed provider.
 */
class IdentityProviderTest {

    @Test
    void resolvesAnonymousIdentityWhenAuthIsOff() {
        Identity identity = new AnonymousIdentityProvider()
                .identityOf(MockServerWebExchange.from(MockServerHttpRequest.get("/api/incidents").build()));

        assertThat(identity.subject()).isEqualTo("anonymous");
        assertThat(identity.roles()).isEmpty();
        assertThat(identity.hasRole("vykronis-approver")).isFalse();
        assertThat(identity.scopes()).isEmpty();
    }

    @Test
    void currentIdentityDelegatesToTheMockedProvider() {
        IdentityProvider provider = mock(IdentityProvider.class);
        Identity expected = new Identity("ops-user", "ops", Set.of("vykronis-user", "vykronis-approver"),
                Set.of("api.read", "remediation.execute"), false);
        when(provider.identityOf(any())).thenReturn(expected);

        Identity identity = new CurrentIdentity(provider)
                .identityOf(MockServerWebExchange.from(MockServerHttpRequest.get("/api/incidents").build()));

        assertThat(identity).isEqualTo(expected);
        assertThat(identity.hasRole("vykronis-approver")).isTrue();
        assertThat(identity.hasScope("remediation.execute")).isTrue();
    }
}