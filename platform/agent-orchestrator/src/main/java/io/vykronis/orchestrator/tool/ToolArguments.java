package io.vykronis.orchestrator.tool;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Strict argument coercion for tool invocations. Model/provider-supplied values
 * are treated as untrusted: required keys must be present, identifiers must be
 * UUIDs and instants must parse — otherwise the invocation is rejected with an
 * {@link IllegalArgumentException} (no HTTP call is made).
 */
final class ToolArguments {

    private ToolArguments() {
    }

    static String require(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            throw new IllegalArgumentException("Missing argument: " + key);
        }
        Object value = arguments.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing argument: " + key);
        }
        return value.toString();
    }

    static String optional(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    static UUID requireUuid(Map<String, Object> arguments, String key) {
        String raw = require(arguments, key);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(key + " must be a valid UUID, got: " + raw);
        }
    }

    static Instant requireInstant(Map<String, Object> arguments, String key) {
        String raw = require(arguments, key);
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " must be an ISO-8601 instant, got: " + raw);
        }
    }
}