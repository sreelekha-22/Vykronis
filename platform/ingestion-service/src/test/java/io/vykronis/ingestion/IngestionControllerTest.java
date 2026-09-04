package io.vykronis.ingestion;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vykronis.common.json.Json;
import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.EventType;
import io.vykronis.contracts.model.JfrRecord;
import io.vykronis.contracts.model.ObservabilityEvent;
import io.vykronis.contracts.model.TraceSpan;
import io.vykronis.contracts.model.TraceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IngestionControllerTest {

    private final KafkaTemplate<String, ObservabilityEvent> metricsTemplate = mock(KafkaTemplate.class);
    private KafkaTemplate<String, TraceSpan> traceSpanTemplate;
    private KafkaTemplate<String, JfrRecord> jfrRecordTemplate;
    private IngestionController controller;

    @BeforeEach
    void setUp() {
        traceSpanTemplate = mock(KafkaTemplate.class);
        jfrRecordTemplate = mock(KafkaTemplate.class);
        controller = new IngestionController(metricsTemplate, traceSpanTemplate, jfrRecordTemplate);
    }

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
        var response = controller.ingest(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ObservabilityEvent> value = ArgumentCaptor.forClass(ObservabilityEvent.class);
        verify(metricsTemplate).send(topic.capture(), key.capture(), value.capture());

        assertThat(topic.getValue()).isEqualTo(Topics.METRICS);
        assertThat(key.getValue()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(value.getValue()).isEqualTo(event);
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsEntry("id", event.id().toString());
    }

    @Test
    void ingestTraceProducesToObsTracesKeyedByTraceId() {
        TraceSpan span = new TraceSpan(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "trace-123",
                "payment-service",
                Env.PROD,
                "checkout",
                Instant.parse("2026-09-04T10:00:00Z"),
                42L,
                TraceStatus.OK,
                null,
                Map.of("http.method", "POST"));

        var response = controller.ingestTrace(span);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TraceSpan> value = ArgumentCaptor.forClass(TraceSpan.class);
        verify(traceSpanTemplate).send(topic.capture(), key.capture(), value.capture());

        assertThat(topic.getValue()).isEqualTo(Topics.TRACES);
        assertThat(key.getValue()).isEqualTo("trace-123");
        assertThat(value.getValue()).isEqualTo(span);
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsEntry("id", span.id().toString());
    }

    @Test
    void ingestJfrProducesToObsJfrKeyedByServiceId() {
        JfrRecord jfr = new JfrRecord(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "demo-service",
                Env.DEV,
                "alloc-chunk-1.jfr",
                new byte[]{0x01, 0x02, 0x03, 0x04, 0x05},
                Instant.parse("2026-09-04T10:00:00Z"));

        var response = controller.ingestJfr(jfr);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JfrRecord> value = ArgumentCaptor.forClass(JfrRecord.class);
        verify(jfrRecordTemplate).send(topic.capture(), key.capture(), value.capture());

        assertThat(topic.getValue()).isEqualTo(Topics.JFR);
        assertThat(key.getValue()).isEqualTo("demo-service");
        assertThat(value.getValue()).isEqualTo(jfr);
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsEntry("id", jfr.id().toString());
    }
}