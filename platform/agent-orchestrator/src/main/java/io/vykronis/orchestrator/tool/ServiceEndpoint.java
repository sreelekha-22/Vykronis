package io.vykronis.orchestrator.tool;

import java.util.List;

/**
 * A service an agent tool may call. The {@code name} is what tools reference in
 * code (e.g. {@code "incident"}); {@code baseUrl} is where the service lives and
 * {@code allowedPathPrefixes} is the ONLY set of path prefixes this endpoint
 * will ever be asked to serve. Everything outside it is rejected before a
 * connection is attempted.
 *
 * @param name                 logical endpoint name used by tools
 * @param baseUrl              base URL of the allow-listed service
 * @param allowedPathPrefixes  non-empty allow-list of absolute path prefixes
 */
public record ServiceEndpoint(String name, String baseUrl, List<String> allowedPathPrefixes) {
}