package io.vykronis.contracts.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A single span of an OTel-style distributed trace, persisted on
 * {@code obs.traces}.
 *
 * @param id         unique span id
 * @param traceId    id of the trace this span belongs to (Kafka partition key,
 *                   ordering per trace)
 * @param serviceId  logical service that produced the span
 * @param env        environment the span originated from
 * @param name       span operation name, e.g. {@code checkout}
 * @param startTime  span start instant
 * @param durationMs span duration in milliseconds
 * @param status     outcome of the span
 * @param parentSpanId optional id of the parent span in the same trace
 * @param attributes key/value attributes captured on the span (may be empty)
 */
public record TraceSpan(
        UUID id,
        String traceId,
        String serviceId,
        Env env,
        String name,
        Instant startTime,
        long durationMs,
        TraceStatus status,
        String parentSpanId,
        Map<String, String> attributes
) {
    public TraceSpan {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (traceId == null) {
            throw new IllegalArgumentException("traceId must not be null");
        }
        if (serviceId == null) {
            throw new IllegalArgumentException("serviceId must not be null");
        }
        if (env == null) {
            throw new IllegalArgumentException("env must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must not be null");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
    }
}