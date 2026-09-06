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
 * Phase 6 Unit 2 — a remediation command must map to a compose step. The
 * executor talks to Docker only through the {@link CommandRunner} seam, so the
 * tests pin the mapping with a fake runner and never touch a docker daemon.
 */
class ComposeExecutorTest {

    private final ComposeOptions options =
            new ComposeOptions(List.of("infra/compose/docker-compose.yml", "infra/compose/docker-compose.apps.yml"));

    private final FakeStore store = new FakeStore();

    private RemediationCommand command(RemediationAction action, UUID commandId) {
        return new RemediationCommand(
                commandId, "inc-7", action, "payment-service", Env.PROD,
                "1.3.1", Instant.parse("2026-09-07T10:00:00Z"), "ops");
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
        private final CommandOutcome outcome;
        List<String> captured;

        FakeRunner(CommandOutcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public CommandOutcome run(List<String> argv) {
            captured = List.copyOf(argv);
            return outcome;
        }
    }

    @Test
    void rollbackCommandMapsToComposeUpForTheTargetService() {
        FakeRunner runner = new FakeRunner(new CommandOutcome(true, 0, "creating payment-service ... done"));
        ComposeExecutor executor = new ComposeExecutor(runner, store, options);

        RemediationResult result = executor.execute(command(RemediationAction.ROLLBACK, UUID.randomUUID()));

        assertThat(runner.captured).containsExactly(
                "docker", "compose",
                "-f", "infra/compose/docker-compose.yml",
                "-f", "infra/compose/docker-compose.apps.yml",
                "up", "-d", "--no-deps", "payment-service");
        assertThat(result.outcome()).isEqualTo(RemediationOutcome.COMPLETED);
        assertThat(result.detail()).contains("creating payment-service ... done");
    }

    @Test
    void restartCommandMapsToComposeRestartForTheTargetService() {
        FakeRunner runner = new FakeRunner(new CommandOutcome(true, 0, "restarting"));
        ComposeExecutor executor = new ComposeExecutor(runner, store, options);

        RemediationResult result = executor.execute(command(RemediationAction.RESTART, UUID.randomUUID()));

        assertThat(runner.captured).containsExactly(
                "docker", "compose",
                "-f", "infra/compose/docker-compose.yml",
                "-f", "infra/compose/docker-compose.apps.yml",
                "restart", "payment-service");
        assertThat(result.outcome()).isEqualTo(RemediationOutcome.COMPLETED);
    }

    @Test
    void failedDockerStepMapsToFailedResult() {
        FakeRunner runner = new FakeRunner(new CommandOutcome(true, 1, "service not found"));
        ComposeExecutor executor = new ComposeExecutor(runner, store, options);

        RemediationResult result = executor.execute(command(RemediationAction.ROLLBACK, UUID.randomUUID()));

        assertThat(result.outcome()).isEqualTo(RemediationOutcome.FAILED);
        assertThat(result.detail()).contains("service not found");
    }

    @Test
    void completedStepIsStoredKeyedByCommandId() {
        UUID commandId = UUID.randomUUID();
        FakeRunner runner = new FakeRunner(new CommandOutcome(true, 0, "done"));
        ComposeExecutor executor = new ComposeExecutor(runner, store, options);

        executor.execute(command(RemediationAction.ROLLBACK, commandId));

        assertThat(store.findByCommandId(commandId))
                .hasValueSatisfying(record -> assertThat(record.outcome()).isEqualTo(RemediationOutcome.COMPLETED));
    }
}