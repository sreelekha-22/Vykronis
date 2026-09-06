package io.vykronis.remediation;

import io.vykronis.remediation.executor.KubernetesOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KubernetesConfigTest {

    @Test
    void defaultsProduceVyKronisNamespaceAndKubectlBinary() {
        KubernetesConfig config = new KubernetesConfig();

        KubernetesOptions options = config.kubernetesOptions("vykronis", "kubectl");

        assertThat(options.namespace()).isEqualTo("vykronis");
        assertThat(options.kubectlBinary()).isEqualTo("kubectl");
    }

    @Test
    void allowsOverridingTheNamespace() {
        KubernetesConfig config = new KubernetesConfig();

        KubernetesOptions options = config.kubernetesOptions("prod", "kubectl");

        assertThat(options.namespace()).isEqualTo("prod");
    }

    @Test
    void rejectsBlankValues() {
        KubernetesConfig config = new KubernetesConfig();

        assertThatThrownBy(() -> config.kubernetesOptions(" ", "kubectl"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.kubernetesOptions("vykronis", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
