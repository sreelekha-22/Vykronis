package io.vykronis.remediation.executor;

import java.util.List;

/**
 * Seam between the executor and the operating system. Production uses
 * {@link ProcessCommandRunner}; tests substitute a fake so docker is never
 * spawned from a unit test.
 */
@FunctionalInterface
public interface CommandRunner {

    CommandOutcome run(List<String> argv);
}