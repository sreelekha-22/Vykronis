package io.vykronis.remediation.executor;

import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationResult;

/**
 * SPI for performing a {@link RemediationCommand} on live infrastructure.
 *
 * <p>The only implementation in Vykronis is the Compose executor: remediation
 * happens against Docker Compose and nothing else in the platform ever talks
 * to a docker daemon. Implementations must be idempotent per
 * {@link RemediationCommand#commandId()} — replaying a command is a no-op that
 * returns the already-stored result.</p>
 */
public interface RemediationExecutor {

    /**
     * Execute the command exactly once, returning either a fresh result or the
     * previously stored result when this {@code commandId} has already run.
     */
    RemediationResult execute(RemediationCommand command);
}