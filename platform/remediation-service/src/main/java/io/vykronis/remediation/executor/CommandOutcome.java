package io.vykronis.remediation.executor;

/**
 * Result of running an external command (e.g. {@code docker compose ...}).
 *
 * @param exitedNormally whether the process terminated (as opposed to timing
 *                       out or failing to start)
 * @param exitValue      the process exit code when {@code exitedNormally}
 * @param output         merged stdout/stderr of the process
 */
public record CommandOutcome(boolean exitedNormally, int exitValue, String output) {

    public boolean success() {
        return exitedNormally && exitValue == 0;
    }
}