package io.vykronis.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vykronis.common.json.Json;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;

/**
 * Serde helpers for Kafka Streams. Uses the shared {@link Json#mapper()} so
 * contract records (with {@link java.time.Instant} and {@link
 * com.fasterxml.jackson.databind.JsonNode} fields) serialize/deserialize
 * consistently with the rest of the platform.
 */
final class ContractSerdes {

    private static final ObjectMapper MAPPER = Json.mapper();

    private ContractSerdes() {
    }

    static <T> Serde<T> json(Class<T> type) {
        Serializer<T> serializer = (topic, data) -> {
            if (data == null) {
                return null;
            }
            try {
                return MAPPER.writeValueAsBytes(data);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };
        Deserializer<T> deserializer = (topic, data) -> {
            if (data == null) {
                return null;
            }
            try {
                return MAPPER.readValue(data, type);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        return Serdes.serdeFrom(serializer, deserializer);
    }
}
