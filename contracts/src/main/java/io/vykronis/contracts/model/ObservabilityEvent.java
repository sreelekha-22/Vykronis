package io.vykronis.contracts.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical observability event ingested across the whole platform.
 *
 * <p>This is the single shared Kafka payload across services (see the
 * {@code observability-event.schema.json} contract). Services do not share
 * domain classes beyond these contracts.</p>
 *
 * <p>Configuration note: deserialize with an {@code ObjectMapper} that has the
 * {@code JavaTimeModule} registered so {@link Instant} fields round-trip as
 * ISO-8601 strings.</p>
 *
 * @param id        unique event id (used for consumer idempotency)
 * @param timestamp when the event happened (ISO-8601 instant)
 * @param source    emitter, e.g. {@code order-service}
 * @param serviceId logical service id (Kafka partition key, ordering per service)
 * @param env       environment the event originated from
 * @param type      kind of telemetry
 * @param payload   free-form JSON body (metric sample / log line / trace span /
 *                  JFR event / deployment record)
 * @param traceId   optional W3C-style trace id linking to a distributed trace
 */
public record ObservabilityEvent(
        UUID id,
        Instant timestamp,
        String source,
        String serviceId,
        Env env,
        EventType type,
        JsonNode payload,
        @JsonProperty("traceId") String traceId
) {
    public ObservabilityEvent {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (serviceId == null) {
            throw new IllegalArgumentException("serviceId must not be null");
        }
        if (env == null) {
            throw new IllegalArgumentException("env must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }
}
