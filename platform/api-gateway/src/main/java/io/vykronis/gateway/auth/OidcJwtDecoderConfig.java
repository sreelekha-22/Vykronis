package io.vykronis.gateway.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Reactive JWT decoder for the OIDC resource server. Only created when
 * {@code vykronis.auth.enabled} is {@code true}; without it the gateway runs
 * open (anonymous identity) for the daily dev loop.
 */
@Configuration(proxyBeanMethods = false)
public class OidcJwtDecoderConfig {

    @Bean
    @ConditionalOnProperty(name = "vykronis.auth.enabled", havingValue = "true")
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${vykronis.auth.jwk-set-uri}") String jwkSetUri,
            @Value("${vykronis.auth.issuer-uri:}") String issuerUri) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        if (issuerUri != null && !issuerUri.isBlank()) {
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        }
        return decoder;
    }
}