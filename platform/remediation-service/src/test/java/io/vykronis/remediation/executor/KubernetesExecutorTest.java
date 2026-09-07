package io.vykronis.remediation.executor;

import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.RemediationAction;
import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationOutcome;
import io.vykronis.contracts.model.RemediationResult;
import io.vykronis.remediation.store.RemediationRecord;
import io.vykronis.remediation.store.RemediationRecordStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7 Unit 2 - a KubernetesExecutor that issues the same logical remediation
 * as the {@link ComposeExecutor}. The test pins the equivalence contract
 * before the implementation lands (RED first).
 */
class KubernetesExecutorTest {

    private final KubernetesOptions options = new KubernetesOptions("vykronis", "kubectl");

    private final FakeStore store = new FakeStore();

    private RemediationCommand command(RemediationAction action, UUID commandId) {
        return new RemediationCommand(
                commandId, "inc-7", action, "payment-service", Env.PROD,
                null, Instant.parse("2026-09-07T10:00:00Z"), "ops");
    }

    private RemediationCommand command(RemediationAction action, UUID commandId, String targetVersion) {
        return new RemediationCommand(
                commandId, "inc-7", action, "payment-service", Env.PROD,
                targetVersion, Instant.parse("2026-09-07T10:00:00Z"), "ops");
    }

    private static final class FakeStore implements RemediationRecordStore {
        private final ConcurrentHashMap<String, RemediationRecord> rows = new ConcurrentHashMap<>();

        @Override
        public Optional<RemediationRecord> findByCommandId(UUID commandId) {
            return Optional.ofNullable(rows.get(commandId.toString()));
        }

        @Override
        public void save(RemediationRecord record) {
            rows.put(record.commandId().toString(), record);
        }
    }

    private static final class FakeRunner implements CommandRunner {
        private final boolean success;
        List<String> captured;

        FakeRunner(boolean success) {
            this.success = success;
        }

        @Override
        public CommandOutcome run(List<String> argv) {
            captured = List.copyOf(argv);
            return new CommandOutcome(true, success ? 0 : 1, success ? "ok" : "boom");
        }
    }

    @Test
    void rollbackCommandMapsToKubectlRolloutUndoForTheTargetService() {
        FakeRunner runner = new FakeRunner(true);
        KubernetesExecutor executor = new KubernetesExecutor(runner, store, options);

        executor.execute(command(RemediationAction.ROLLBACK, UUID.randomUUID()));

        assertThat(runner.captured).containsExactly(
                "kubectl", "rollout", "undo", "deployment/payment-service", "-n", "vykronis");
    }

    @Test
    void restartCommandMapsToKubectlRolloutRestartForTheTargetService() {
        FakeRunner runner = new FakeRunner(true);
        KubernetesExecutor executor = new KubernetesExecutor(runner, store, options);

        executor.execute(command(RemediationAction.RESTART, UUID.randomUUID()));

        assertThat(runner.captured).containsExactly(
                "kubectl", "rollout", "restart", "deployment/payment-service", "-n", "vykronis");
    }

    @Test
    void rollbackWithTargetVersionPinsToRevision() {
        FakeRunner runner = new FakeRunner(true);
        KubernetesExecutor executor = new KubernetesExecutor(runner, store, options);

        executor.execute(command(RemediationAction.ROLLBACK, UUID.randomUUID(), "3"));

        assertThat(runner.captured).containsExactly(
                "kubectl", "rollout", "undo", "deployment/payment-service", "-n", "vykronis",
                "--to-revision=3");
    }

    @Test
    void rollbackWithoutTargetVersionUndoesToPreviousRevision() {
        FakeRunner runner = new FakeRunner(true);
        KubernetesExecutor executor = new KubernetesExecutor(runner, store, options);

        executor.execute(command(RemediationAction.ROLLBACK, UUID.randomUUID()));

        assertThat(runner.captured).containsExactly(
                "kubectl", "rollout", "undo", "deployment/payment-service", "-n", "vykronis");
    }

    @Test
    void restartIgnoresTargetVersionBecauseRolloutRestartHasNoRevisionSemantics() {
        FakeRunner runner = new FakeRunner(true);
        KubernetesExecutor executor = new KubernetesExecutor(runner, store, options);

        executor.execute(command(RemediationAction.RESTART, UUID.randomUUID(), "3"));

        assertThat(runner.captured).doesNotContain("--to-revision=3");
        assertThat(runner.captured).containsExactly(
                "kubectl", "rollout", "restart", "deployment/payment-service", "-n", "vykronis");
    }

    @Test
    void kubernetesAndComposeIssueEquivalentCommandsForTheSameInput() {
        ComposeOptions composeOptions = new ComposeOptions(
                List.of("infra/compose/docker-compose.yml", "infra/compose/docker-compose.apps.yml"));
        ComposeExecutor composeExecutor = new ComposeExecutor(new FakeRunner(true), store, composeOptions);
        KubernetesExecutor kubernetesExecutor = new KubernetesExecutor(new FakeRunner(true), store, options);

        UUID commandId = UUID.randomUUID();
        RemediationCommand cmd = command(RemediationAction.ROLLBACK, commandId);

        RemediationResult composeResult = composeExecutor.execute(cmd);
        RemediationResult kubernetesResult = kubernetesExecutor.execute(cmd);

        assertThat(kubernetesResult.outcome()).isEqualTo(composeResult.outcome());
        assertThat(kubernetesResult.commandId()).isEqualTo(composeResult.commandId());
        assertThat(kubernetesResult.outcome()).isEqualTo(RemediationOutcome.COMPLETED);
    }
}
