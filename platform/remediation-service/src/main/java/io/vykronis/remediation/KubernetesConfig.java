package io.vykronis.remediation;

import io.vykronis.remediation.executor.KubernetesOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the Kubernetes target (namespace + kubectl binary) from configuration
 * into a {@link KubernetesOptions} used by the {@link KubernetesExecutor}.
 *
 * <p>Only active when the deployment target is {@code kubernetes} (i.e. running
 * on a kind/k3d/Helm cluster); otherwise the {@link ComposeExecutor} owns the
 * {@code RemediationExecutor} bean.</p>
 */
@Configuration
@ConditionalOnProperty(name = "vykronis.remediation.target", havingValue = "kubernetes")
public class KubernetesConfig {

    @Bean
    public KubernetesOptions kubernetesOptions(
            @Value("${vykronis.remediation.kubernetes.namespace:vykronis}") String namespace,
            @Value("${vykronis.remediation.kubernetes.kubectl-binary:kubectl}") String kubectlBinary) {
        return new KubernetesOptions(namespace, kubectlBinary);
    }
}
