package io.vykronis.remediation.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs a command through {@link ProcessBuilder}, merging stderr into stdout so
 * the produced {@link CommandOutcome} carries the full picture (e.g. a docker
 * error message). Guards against hung docker daemons with a timeout.
 */
@Component
public class ProcessCommandRunner implements CommandRunner {

    private final int timeoutSeconds;

    public ProcessCommandRunner(@Value("${remediation.docker.timeout-seconds:120}") int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public CommandOutcome run(List<String> argv) {
        try {
            ProcessBuilder builder = new ProcessBuilder(argv);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandOutcome(false, -1, "command timed out after " + timeoutSeconds + "s");
            }
            return new CommandOutcome(true, process.exitValue(), readOutput(process.getInputStream()));
        } catch (Exception e) {
            return new CommandOutcome(false, -1, "failed to start command: " + e.getMessage());
        }
    }

    private static String readOutput(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }
}