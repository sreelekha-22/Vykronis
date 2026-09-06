package io.vykronis.remediation.executor;

import io.vykronis.contracts.model.RemediationAction;
import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationOutcome;
import io.vykronis.contracts.model.RemediationResult;
import io.vykronis.remediation.store.RemediationRecord;
import io.vykronis.remediation.store.RemediationRecordStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The only component that talks to a docker daemon. Turns a policy-approved
 * {@link RemediationCommand} into a {@code docker compose} invocation and runs
 * it through the {@link CommandRunner} seam.
 *
 * <p>Idempotency: a command id already present in the
 * {@link RemediationRecordStore} is never executed again — the stored result is
 * returned so replays are no-ops (Phase 6 Unit 6 verifies this explicitly).</p>
 */
@Component
public class ComposeExecutor implements RemediationExecutor {

    private final CommandRunner runner;
    private final RemediationRecordStore store;
    private final ComposeOptions options;

    public ComposeExecutor(CommandRunner runner, RemediationRecordStore store, ComposeOptions options) {
        this.runner = runner;
        this.store = store;
        this.options = options;
    }

    @Override
    public RemediationResult execute(RemediationCommand command) {
        var existing = store.findByCommandId(command.commandId());
        if (existing.isPresent()) {
            return existing.get().asResult();
        }

        CommandOutcome outcome = runner.run(commandLine(command));
        Instant completedAt = Instant.now();

        RemediationOutcome result = outcome.success() ? RemediationOutcome.COMPLETED : RemediationOutcome.FAILED;
        RemediationRecord record = RemediationRecord.from(command, result, trim(outcome.output()), completedAt);
        store.save(record);
        return record.asResult();
    }

    /**
     * Maps a {@link RemediationCommand} to the exact {@code docker compose}
     * argv. ROLLBACK recreates the service container ({@code up -d --no-deps})
     * which lifts it to the version pinned in the active compose overlay;
     * RESTART recycles the running container in place.
     */
    List<String> commandLine(RemediationCommand command) {
        List<String> argv = new ArrayList<>();
        argv.add("docker");
        argv.add("compose");
        for (String file : options.composeFiles()) {
            argv.add("-f");
            argv.add(file);
        }
        if (command.action() == RemediationAction.ROLLBACK) {
            argv.add("up");
            argv.add("-d");
            argv.add("--no-deps");
        } else {
            argv.add("restart");
        }
        argv.add(command.serviceId());
        return argv;
    }

    private static String trim(String output) {
        if (output == null) {
            return "";
        }
        String trimmed = output.trim();
        return trimmed.length() > 4000 ? trimmed.substring(0, 4000) : trimmed;
    }
}