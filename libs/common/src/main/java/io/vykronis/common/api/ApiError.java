package io.vykronis.common.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Uniform REST error body returned by Vykronis services.
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param code      machine-readable error code, e.g. {@code NOT_FOUND}
 * @param message   human-readable detail
 * @param traceId   correlation trace id, when available
 * @param ref       optional request/incident reference
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String traceId,
        String ref
) {
    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, null, null);
    }

    public static ApiError of(int status, String code, String message, String traceId) {
        return new ApiError(Instant.now(), status, code, message, traceId, null);
    }

    public static ApiError notFound(String message, UUID ref) {
        return new ApiError(Instant.now(), 404, "NOT_FOUND", message, null, ref.toString());
    }
}
