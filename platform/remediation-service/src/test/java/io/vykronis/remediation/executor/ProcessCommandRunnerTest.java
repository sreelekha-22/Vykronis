package io.vykronis.remediation.executor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a real subprocess through the production code path (no docker required).
 * We use portable commands ({@code java -version} on the PATH), not the docker
 * CLI, to keep the test independent of the host daemon.
 */
class ProcessCommandRunnerTest {

    @Test
    void runningARealCommandCapturesExitZeroAndOutput() {
        ProcessCommandRunner runner = new ProcessCommandRunner(30);

        CommandOutcome outcome = runner.run(echoCommand("hello from the runner"));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.exitValue()).isEqualTo(0);
        assertThat(outcome.output()).contains("hello from the runner");
    }

    @Test
    void aFailingCommandSurfacesTheNonZeroExitCode() {
        ProcessCommandRunner runner = new ProcessCommandRunner(30);

        CommandOutcome outcome = runner.run(failingCommand());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.exitValue()).isNotEqualTo(0);
    }

    @Test
    void aHungCommandIsKilledAndReportedAsTimedOut() throws Exception {
        ProcessCommandRunner runner = new ProcessCommandRunner(1);

        long started = System.nanoTime();
        CommandOutcome outcome = runner.run(sleepCommand(10));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.exitValue()).isEqualTo(-1);
        assertThat(outcome.output()).contains("timed out");
        assertThat(elapsedMillis).isLessThan(10_000);
    }

    private static List<String> echoCommand(String text) {
        if (isWindows()) {
            return List.of("cmd", "/c", "echo", text);
        }
        return List.of("sh", "-c", "echo " + text);
    }

    private static List<String> failingCommand() {
        if (isWindows()) {
            return List.of("cmd", "/c", "exit", "7");
        }
        return List.of("sh", "-c", "exit 7");
    }

    private static List<String> sleepCommand(int seconds) {
        if (isWindows()) {
            return List.of("cmd", "/c", "ping", "-n", String.valueOf(seconds + 1), "127.0.0.1");
        }
        return List.of("sh", "-c", "sleep " + seconds);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
