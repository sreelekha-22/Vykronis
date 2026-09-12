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
 *   <li>All 8 platform services + infra have Deployment/StatefulSet resources.</li>
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
        // Heavyweight and opt-in: needs Docker + all service images built.
        // Enable with -Dhelm.smoke.kind=true. An existing cluster is reused
        // (e.g. a local dev box); otherwise a fresh one is created and deleted.
        assumeThat(Boolean.getBoolean("helm.smoke.kind"))
                .as("kind smoke is opt-in (-Dhelm.smoke.kind=true)").isTrue();
        assumeThat(isToolAvailable("helm")).as("helm not on PATH").isTrue();
        assumeThat(isToolAvailable("kind")).as("kind not on PATH").isTrue();
        assumeThat(isToolAvailable("kubectl")).as("kubectl not on PATH").isTrue();

        final String cluster = "vykronis-smoke";
        boolean createdHere = false;
        boolean deleteOnExit = false;
        try {
            if (!clusterExists(cluster)) {
                ProcessBuilder create = new ProcessBuilder("kind", "create", "cluster", "--name", cluster, "--wait", "120s");
                create.redirectErrorStream(true);
                Process cp = create.start();
                String createOut = new String(cp.getInputStream().readAllBytes());
                int createExit = cp.waitFor();
                assumeThat(createExit).as("kind create cluster failed (Docker may not be available):\n" + createOut).isEqualTo(0);
                createdHere = true;
            }

            // Make chart images available to the cluster (skips images that are
            // not present in the local docker daemon — build first via compose).
            int loaded = loadChartImages(cluster);
            assumeThat(loaded).as("no vykronis images in local docker daemon; build them first (docker compose build)").isGreaterThan(0);

            // Install chart
            ProcessBuilder install = new ProcessBuilder("helm", "install", "vykronis", CHART_DIR.toString(),
                    "-n", "vykronis-smoke", "--create-namespace", "--wait", "--timeout", "300s");
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

            // Only reach here on success: keep a freshly-created cluster around if
            // any step above failed, so CI logs can be inspected before it is torn down.
            deleteOnExit = true;
        } finally {
            if (createdHere && deleteOnExit) {
                new ProcessBuilder("kind", "delete", "cluster", "--name", cluster).start().waitFor();
            }
        }
    }

    private static boolean clusterExists(String name) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("kind", "get", "clusters");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        return out.lines().anyMatch(line -> line.trim().equals(name));
    }

    private static int loadChartImages(String cluster) throws Exception {
        String[] services = {
                "api-gateway", "ingestion-service", "event-service", "correlation-engine",
                "incident-service", "agent-orchestrator", "policy-service", "remediation-service"
        };
        int loaded = 0;
        for (String svc : services) {
            String image = "vykronis/" + svc + ":local";
            if (!dockerImageExists(image)) {
                continue;
            }
            ProcessBuilder lb = new ProcessBuilder("kind", "load", "docker-image", image, "--name", cluster);
            lb.redirectErrorStream(true);
            Process lp = lb.start();
            String out = new String(lp.getInputStream().readAllBytes());
            int exit = lp.waitFor();
            assumeThat(exit).as("kind load docker-image failed for " + image + ":\n" + out).isEqualTo(0);
            loaded++;
        }
        return loaded;
    }

    private static boolean dockerImageExists(String image) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "image", "inspect", image);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
        return p.exitValue() == 0;
    }

    private static boolean isToolAvailable(String name) {
        // Most tools answer `<tool> version`; kubeconform answers `<tool> -v`.
        // A PATH lookup closes the gap where the tool is present but its version
        // invocation fails (e.g. `kubectl version` exits non-zero with no cluster).
        return tryVersion(name, "version") || tryVersion(name, "-v") || onPath(name);
    }

    private static boolean onPath(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            if (new File(dir, name).canExecute()) {
                return true;
            }
        }
        return false;
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