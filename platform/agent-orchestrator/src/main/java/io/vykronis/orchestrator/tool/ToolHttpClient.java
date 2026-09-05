package io.vykronis.orchestrator.tool;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * THE only HTTP client agent tools are allowed to use. It is IDENTITY-FIRST:
 * before any connection is attempted it verifies that (a) the target is one of
 * the configured endpoints and (b) the normalized request path is inside that
 * endpoint's allow-list. Everything else raises
 * {@link ToolEndpointViolationException} without ever reaching the network — so
 * a tool can never reach Docker, Kubernetes or any admin surface, even if a
 * prompt persuades the model to try.
 *
 * <p>Concurrency: immutable after construction, safe to share.
 */
public class ToolHttpClient {

    private final RestClient client;
    private final Map<String, ServiceEndpoint> endpoints;

    public ToolHttpClient(RestClient.Builder builder, List<ServiceEndpoint> endpoints) {
        Objects.requireNonNull(builder, "builder");
        this.endpoints = endpoints.stream()
                .collect(Collectors.toUnmodifiableMap(ServiceEndpoint::name, e -> e));
        if (this.endpoints.isEmpty()) {
            throw new IllegalArgumentException("At least one allow-listed endpoint must be configured");
        }
        for (ServiceEndpoint e : endpoints) {
            if (e.baseUrl() == null || e.baseUrl().isBlank()) {
                throw new IllegalArgumentException("Endpoint " + e.name() + " has no base URL");
            }
            if (e.allowedPathPrefixes() == null || e.allowedPathPrefixes().isEmpty()) {
                throw new IllegalArgumentException("Endpoint " + e.name()
                        + " must declare a non-empty allow-list of path prefixes");
            }
        }
        this.client = builder.build();
    }

    /**
     * Performs an allow-listed GET against {@code endpointName} at {@code path}
     * and returns the response body. Query values are percent-encoded.
     */
    public String get(String endpointName, String path, Map<String, String> query) {
        ServiceEndpoint endpoint = requireEndpoint(endpointName);
        String normalized = requireAllowedPath(endpoint, path);
        URI uri = UriComponentsBuilder.fromUriString(trimTrailingSlash(endpoint.baseUrl()))
                .path(normalized)
                .queryParams(toMultiMap(query))
                .encode()
                .build()
                .toUri();
        return client.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
    }

    private ServiceEndpoint requireEndpoint(String endpointName) {
        ServiceEndpoint endpoint = endpoints.get(endpointName);
        if (endpoint == null) {
            throw new ToolEndpointViolationException(
                    "Endpoint '" + endpointName + "' is not in the configured allow-list. "
                            + "Allowed endpoints: " + endpoints.keySet());
        }
        return endpoint;
    }

    private String requireAllowedPath(ServiceEndpoint endpoint, String path) {
        String normalized = normalizePath(path);
        boolean allowed = endpoint.allowedPathPrefixes().stream()
                .map(this::normalizePath)
                .anyMatch(prefix -> normalized.equals(prefix) || normalized.startsWith(prefix + "/"));
        if (!allowed) {
            throw new ToolEndpointViolationException(
                    "Path '" + path + "' on endpoint '" + endpoint.name() + "' is not allow-listed. "
                            + "Allowed prefixes: " + endpoint.allowedPathPrefixes());
        }
        return normalized;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new ToolEndpointViolationException("Tool invoked with a blank path");
        }
        int query = path.indexOf('?');
        int fragment = path.indexOf('#');
        int end = path.length();
        if (query >= 0) {
            end = Math.min(end, query);
        }
        if (fragment >= 0) {
            end = Math.min(end, fragment);
        }
        String p = path.substring(0, end);
        if (!p.startsWith("/")) {
            throw new ToolEndpointViolationException("Path must be absolute: " + path);
        }
        for (String segment : p.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new ToolEndpointViolationException(
                        "Path traversal is not allow-listed: " + path);
            }
        }
        return p;
    }

    private MultiValueMap<String, String> toMultiMap(Map<String, String> query) {
        MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
        if (query != null) {
            query.entrySet().stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                    .forEach(e -> result.add(e.getKey(), e.getValue()));
        }
        return result;
    }

    private static String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}