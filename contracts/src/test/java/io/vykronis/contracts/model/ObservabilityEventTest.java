package io.vykronis.contracts.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityEventTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void roundTripsThroughJackson() throws Exception {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("latencyMs", 12.5d);
        payload.put("count", 3);

        ObservabilityEvent event = new ObservabilityEvent(
                UUID.randomUUID(),
                Instant.parse("2026-08-29T10:00:00Z"),
                "order-service",
                "order-service",
                Env.DEV,
                EventType.METRIC,
                payload,
                null);

        String json = mapper.writeValueAsString(event);
        ObservabilityEvent parsed = mapper.readValue(json, ObservabilityEvent.class);

        assertThat(parsed).isEqualTo(event);
        assertThat(parsed.timestamp().toString()).startsWith("2026-08-29T10:00:00");
        assertThat(parsed.payload()).isInstanceOf(JsonNode.class);
        assertThat(parsed.payload().get("latencyMs").asDouble()).isEqualTo(12.5d);
    }
}
