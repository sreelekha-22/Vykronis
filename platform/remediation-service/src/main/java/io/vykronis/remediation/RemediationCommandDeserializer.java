package io.vykronis.remediation;

import com.fasterxml.jackson.databind.JsonNode;
import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.RemediationCommand;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

/**
 * Tolerant deserializer for the single {@code obs.remediation} command bus.
 * Returns a {@link RemediationCommand} only for records that carry the
 * {@code action} field; anything else ({@link io.vykronis.contracts.model
 * .RemediationResult} records, malformed bytes) is skipped as {@code null} so
 * the consumer never poisons itself on foreign record types.
 */
public final class RemediationCommandDeserializer implements Deserializer<RemediationCommand> {

    @Override
    public RemediationCommand deserialize(String topic, Headers headers, byte[] data) {
        return deserialize(topic, data);
    }

    @Override
    public RemediationCommand deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        JsonNode node;
        try {
            node = Json.mapper().readTree(data);
        } catch (Exception e) {
            return null;
        }
        if (node == null || !node.has("action")) {
            return null;
        }
        try {
            return Json.mapper().convertValue(node, RemediationCommand.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public void close() {
    }
}