package io.vykronis.remediation.executor;

import java.util.List;

/**
 * Docker Compose target: the top-level file plus the apps overlay. The
 * executor passes every file to {@code docker compose -f <file> ...} so
 * services can be addressed by their app-profile name.
 */
public record ComposeOptions(List<String> composeFiles) {

    public ComposeOptions {
        if (composeFiles == null || composeFiles.isEmpty()) {
            throw new IllegalArgumentException("composeFiles must list at least one compose file");
        }
    }
}