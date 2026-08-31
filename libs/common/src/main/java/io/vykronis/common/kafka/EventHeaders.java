package io.vykronis.common.kafka;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Standard Kafka record header names and helpers used across Vykronis
 * producers/consumers. Headers carry lightweight correlation metadata without
 * bloating the record value.
 */
public final class EventHeaders {

    /** Trace id linking an event to its distributed trace. */
    public static final String TRACE_ID = "vykronis.traceId";

    /** Original event id, useful for dedup/replay without reparsing the body. */
    public static final String EVENT_ID = "vykronis.eventId";

    /** Service that produced the record (falls back to the partition key). */
    public static final String SOURCE = "vykronis.source";

    private EventHeaders() {
    }

    public static Optional<String> get(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) {
            return Optional.empty();
        }
        return Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }

    public static void put(Headers headers, String key, String value) {
        if (value != null) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
