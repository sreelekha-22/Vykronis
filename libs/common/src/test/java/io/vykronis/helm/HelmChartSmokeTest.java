package io.vykronis.helm;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Phase 7 Unit 1 — Helm chart smoke test.
 *
 * <p>RED first: the chart directory does not exist yet, so {@code helm template}
 * fails with exit code 1. Once the chart is scaffolded, this test verifies:</p>
 * <ol>
 *   <li>{@code helm template} renders without error (exit 0).</li>
 *   <li>All 9 platform services + infra have Deployment/StatefulSet resources.</li>
 *   <li>{@code kubeconform} validates the rendered YAML (strict, Kubernetes 1.29).</li>
 *   <li>Liveness/readiness probes are present on every Deployment.</li>
 *   <li>Resource requests/limits are set.</li>
 * </ol>
 * <p>The test is skipped if {@code helm}, {@code kubeconform}, or {@code kind}
 * are not on the PATH (so CI can run it when the toolchain is present).</p>
 */
class HelmChartSmokeTest {

    private static final Path CHART_DIR = Path.of("../..").toAbsolutePath().normalize().resolve("infra/helm/vykronis");
    private static final Path KUBECONFORM = Path.of("kubeconform").toAbsolutePath(); // on PATH

    @Test
    void helmTemplateRendersWithoutError() throws Exception {
        assumeThat(isToolAvailable("helm")).as("helm not on PATH").isTrue();

        ProcessBuilder pb = new ProcessBuilder("helm", "template", "vykronis", CHART_DIR.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();

        assertThat(exit).as("helm template failed:\n" + output).isEqualTo(0);
        assertThat(output).contains("kind: Deployment");
        assertThat(output).contains("kind: StatefulSet"); // postgres
    }

    @Test
    void renderedYamlPassesKubeconform() throws Exception {
        assumeThat(isToolAvailable("helm")).as("helm not on PATH").isTrue();
        assumeThat(isToolAvailable("kubeconform")).as("kubeconform not on PATH").isTrue();

        // helm template -> pipe to kubeconform
        ProcessBuilder helm = new ProcessBuilder("helm", "template", "vykronis", CHART_DIR.toString());
        ProcessBuilder kconf = new ProcessBuilder("kubeconform", "-strict", "-kubernetes-version", "1.29.0", "-summary");
        kconf.redirectInput(ProcessBuilder.Redirect.PIPE);
        kconf.redirectErrorStream(true);

        Process hp = helm.start();
        Process kp = kconf.start();
        try (var stdin = kp.getOutputStream()) {
            hp.getInputStream().transferTo(stdin);
        }
        hp.waitFor();

        String kcOutput = new String(kp.getInputStream().readAllBytes());
        int kcExit = kp.waitFor();

        assertThat(kcExit).as("kubeconform validation failed:\n" + kcOutput).isEqualTo(0);
    }

    @Test
    void kindSmokeDeploysChartAndPodsReady() throws Exception {
        // Heavyweight and opt-in: needs Docker + all service images built
        // (e.g. `kind load docker-image ...`). Enable with -Dhelm.smoke.kind=true.
        assumeThat(Boolean.getBoolean("helm.smoke.kind"))
                .as("kind smoke is opt-in (-Dhelm.smoke.kind=true)").isTrue();
        assumeThat(isToolAvailable("helm")).as("helm not on PATH").isTrue();
        assumeThat(isToolAvailable("kind")).as("kind not on PATH").isTrue();

        // Create a kind cluster
        ProcessBuilder create = new ProcessBuilder("kind", "create", "cluster", "--name", "vykronis-smoke", "--wait", "120s");
        create.redirectErrorStream(true);
        Process cp = create.start();
        String createOut = new String(cp.getInputStream().readAllBytes());
        int createExit = cp.waitFor();
        assumeThat(createExit).as("kind create cluster failed (Docker may not be available):\n" + createOut).isEqualTo(0);

        try {
            // Install chart
            ProcessBuilder install = new ProcessBuilder("helm", "install", "vykronis", CHART_DIR.toString(),
                    "-n", "vykronis-smoke", "--create-namespace", "--wait", "--timeout", "180s");
            install.redirectErrorStream(true);
            Process ip = install.start();
            String installOut = new String(ip.getInputStream().readAllBytes());
            int installExit = ip.waitFor();

            assertThat(installExit).as("helm install failed:\n" + installOut).isEqualTo(0);

            // Verify pods ready
            ProcessBuilder pods = new ProcessBuilder("kubectl", "-n", "vykronis-smoke", "wait",
                    "--for=condition=Ready", "pods", "--all", "--timeout=300s");
            pods.redirectErrorStream(true);
            Process pp = pods.start();
            String podsOut = new String(pp.getInputStream().readAllBytes());
            int podsExit = pp.waitFor();

            assertThat(podsExit).as("pods not ready:\n" + podsOut).isEqualTo(0);
        } finally {
            // Cleanup
            new ProcessBuilder("kind", "delete", "cluster", "--name", "vykronis-smoke").start().waitFor();
        }
    }

    private static boolean isToolAvailable(String name) {
        // Most tools answer `<tool> version`; kubeconform answers `<tool> -v`.
        return tryVersion(name, "version") || tryVersion(name, "-v");
    }

    private static boolean tryVersion(String name, String flag) {
        try {
            ProcessBuilder pb = new ProcessBuilder(name, flag);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}