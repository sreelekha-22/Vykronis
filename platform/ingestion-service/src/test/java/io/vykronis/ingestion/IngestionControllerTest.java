package io.vykronis.ingestion;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.EventType;
import io.vykronis.contracts.model.ObservabilityEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IngestionControllerTest {

    @Mock
    private KafkaTemplate<String, ObservabilityEvent> kafkaTemplate;

    @InjectMocks
    private IngestionController controller;

    private ObservabilityEvent sampleEvent() {
        ObjectNode payload = Json.mapper().createObjectNode().put("error_rate", 45.0);
        return new ObservabilityEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Instant.parse("2026-09-03T10:00:00Z"),
                "payment-service",
                "payment-service",
                Env.PROD,
                EventType.METRIC,
                payload,
                "trace-abc");
    }

    @Test
    void ingestProducesEventToMetricsTopicKeyedByEventId() {
        ObservabilityEvent event = sampleEvent();
        controller.ingest(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ObservabilityEvent> value = ArgumentCaptor.forClass(ObservabilityEvent.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), value.capture());

        assertThat(topic.getValue()).isEqualTo("obs.metrics");
        assertThat(key.getValue()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(value.getValue()).isEqualTo(event);
    }
}
