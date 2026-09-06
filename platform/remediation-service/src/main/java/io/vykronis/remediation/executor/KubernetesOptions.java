package io.vykronis.remediation.executor;

/**
 * Static shape of a {@code kubectl} call - which binary and which namespace the
 * KubernetesExecutor shells out to. Mirrors {@link ComposeOptions}: validation
 * happens in the compact constructor; everything else is just getters.
 *
 * @param namespace     target Kubernetes namespace (e.g. {@code vykronis})
 * @param kubectlBinary executable to invoke (defaults to {@code kubectl})
 */
public record KubernetesOptions(String namespace, String kubectlBinary) {
    public KubernetesOptions {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        if (kubectlBinary == null || kubectlBinary.isBlank()) {
            throw new IllegalArgumentException("kubectlBinary must not be blank");
        }
    }
}
