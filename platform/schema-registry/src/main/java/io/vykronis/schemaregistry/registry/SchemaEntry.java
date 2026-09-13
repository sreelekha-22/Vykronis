package io.vykronis.schemaregistry.registry;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * An immutable, versioned registration of a JSON Schema for one Kafka topic.
 * Versions are immutable and monotonic per topic (1 = first).
 */
public record SchemaEntry(String topic, int version, JsonNode schema, Instant registeredAt) {
}