package io.vykronis.ingestion;

import io.vykronis.contracts.model.ObservabilityEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class IngestionController {

    private final KafkaTemplate<String, ObservabilityEvent> kafkaTemplate;

    public IngestionController(KafkaTemplate<String, ObservabilityEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/api/ingest/events")
    public ResponseEntity<Map<String, String>> ingest(@RequestBody ObservabilityEvent event) {
        kafkaTemplate.send("obs.metrics", event.id().toString(), event);
        return ResponseEntity.accepted()
                .body(Map.of("id", event.id().toString()));
    }
}