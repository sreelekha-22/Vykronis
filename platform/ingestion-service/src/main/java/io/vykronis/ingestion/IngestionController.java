package io.vykronis.ingestion;

import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.JfrRecord;
import io.vykronis.contracts.model.ObservabilityEvent;
import io.vykronis.contracts.model.TraceSpan;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class IngestionController {

    private final KafkaTemplate<String, ObservabilityEvent> metricsTemplate;
    private final KafkaTemplate<String, TraceSpan> traceSpanTemplate;
    private final KafkaTemplate<String, JfrRecord> jfrRecordTemplate;

    public IngestionController(
            KafkaTemplate<String, ObservabilityEvent> metricsTemplate,
            KafkaTemplate<String, TraceSpan> traceSpanTemplate,
            KafkaTemplate<String, JfrRecord> jfrRecordTemplate) {
        this.metricsTemplate = metricsTemplate;
        this.traceSpanTemplate = traceSpanTemplate;
        this.jfrRecordTemplate = jfrRecordTemplate;
    }

    @PostMapping("/api/ingest/events")
    public ResponseEntity<Map<String, String>> ingest(@RequestBody ObservabilityEvent event) {
        metricsTemplate.send(Topics.METRICS, event.id().toString(), event);
        return ResponseEntity.accepted()
                .body(Map.of("id", event.id().toString()));
    }

    @PostMapping("/api/ingest/traces")
    public ResponseEntity<Map<String, String>> ingestTrace(@RequestBody TraceSpan span) {
        traceSpanTemplate.send(Topics.TRACES, span.traceId(), span);
        return ResponseEntity.accepted()
                .body(Map.of("id", span.id().toString()));
    }

    @PostMapping("/api/ingest/jfr")
    public ResponseEntity<Map<String, String>> ingestJfr(@RequestBody JfrRecord record) {
        jfrRecordTemplate.send(Topics.JFR, record.serviceId(), record);
        return ResponseEntity.accepted()
                .body(Map.of("id", record.id().toString()));
    }
}