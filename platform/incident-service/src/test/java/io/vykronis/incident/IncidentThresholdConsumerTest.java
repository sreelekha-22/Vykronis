package io.vykronis.incident;

import io.vykronis.common.ObservabilityEvent;
import io.vykronis.common.contracts.model.EventType;
import io.vykronis.common.contracts.model.Env;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.KafkaTest;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@KafkaTest
class IncidentThresholdConsumerTest {

    @Autowired
    private KafkaTemplate<String, ObservabilityEvent> kafkaTemplate;

    @Test
    void testErrorBurstEventsCanBePersisted() {
        // Given: Send error burst events
        String serviceId = "test-service";
        for (int i = 0; i < 5; i++) {
            ObservabilityEvent event = new ObservabilityEvent(
                UUID.randomUUID(),
                Instant.now(),
                "test-generator",
                serviceId,
                Env.PROD,
                EventType.METRIC,
                io.vykronis.common.json.Json.mapper().createObjectNode()
                    .put("error_rate", 25.0)
                    .put("error_count", 5)
                    .put("window_start", Instant.now().toString())
                    .put("window_end", Instant.now().plusMinutes(1).toString()),
                "trace-" + i
            );
            kafkaTemplate.send("obs.metrics", serviceId, event);
        }

        // Then: Verify events were sent and can be consumed
        var records = KafkaTestUtils.getRecords(kafkaTemplate.getProducerFactory().getConfiguration(), "obs.metrics");
        assertThat(records).hasSize(5);

        for (var record : records) {
            var payload = (com.fasterxml.jackson.databind.JsonNode) record.value();
            assertThat(payload.get("error_rate").asDouble()).isEqualTo(25.0);
            assertThat(payload.get("service").asText()).isEqualTo(serviceId);
        }
    }
}