package io.vykronis.gateway.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the identity seam. The anonymous default is used until a real
 * {@link IdentityProvider} bean exists (the Phase 5 Unit 2 OIDC provider
 * conditionally replaces it), so the dev loop stays open by default.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayAuthConfig {

    @Bean
    @ConditionalOnMissingBean(IdentityProvider.class)
    public IdentityProvider identityProvider() {
        return new AnonymousIdentityProvider();
    }
}