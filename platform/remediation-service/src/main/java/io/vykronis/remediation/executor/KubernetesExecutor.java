package io.vykronis.remediation.executor;

import io.vykronis.contracts.model.RemediationAction;
import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationOutcome;
import io.vykronis.contracts.model.RemediationResult;
import io.vykronis.remediation.store.RemediationRecord;
import io.vykronis.remediation.store.RemediationRecordStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Kubernetes counterpart to {@link ComposeExecutor}: turns a policy-approved
 * {@link RemediationCommand} into a {@code kubectl rollout} call and runs it
 * through the {@link CommandRunner} seam.
 *
 * <p>The two executors are <em>command-equivalent</em>: for the same
 * {@code (action, serviceId, commandId)} they return the same
 * {@link RemediationOutcome} (COMPLETED on exit 0, FAILED otherwise) and
 * the same idempotency-keyed record (Phase 7 Unit 2 verifies this).</p>
 *
 * <ul>
 *   <li>ROLLBACK -> {@code kubectl rollout undo deployment/<svc> -n <ns>}
 *       (with {@code --to-revision} when the command pins a target version)</li>
 *   <li>RESTART  -> {@code kubectl rollout restart deployment/<svc> -n <ns>}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "vykronis.remediation.target", havingValue = "kubernetes")
public class KubernetesExecutor implements RemediationExecutor {

    private final CommandRunner runner;
    private final RemediationRecordStore store;
    private final KubernetesOptions options;

    public KubernetesExecutor(CommandRunner runner, RemediationRecordStore store, KubernetesOptions options) {
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
     * Maps a {@link RemediationCommand} to the exact {@code kubectl} argv.
     * ROLLBACK undoes the current rollout, pinned to {@code --to-revision}
     * when the command carries a {@code targetVersion} (otherwise the previous
     * revision); RESTART recycles pods in place and has no revision semantics.
     */
    List<String> commandLine(RemediationCommand command) {
        List<String> argv = new ArrayList<>();
        argv.add(options.kubectlBinary());
        argv.add("rollout");
        if (command.action() == RemediationAction.ROLLBACK) {
            argv.add("undo");
        } else {
            argv.add("restart");
        }
        argv.add("deployment/" + command.serviceId());
        argv.add("-n");
        argv.add(options.namespace());
        if (command.action() == RemediationAction.ROLLBACK
                && command.targetVersion() != null
                && !command.targetVersion().isBlank()) {
            argv.add("--to-revision=" + command.targetVersion());
        }
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
