package io.vykronis.policy.matrix;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the standard decision matrix as the {@code policy-service} bean.
 */
@Configuration(proxyBeanMethods = false)
public class PolicyMatrixConfig {

    @Bean
    public DecisionMatrix decisionMatrix() {
        return DecisionMatrix.standard();
    }
}