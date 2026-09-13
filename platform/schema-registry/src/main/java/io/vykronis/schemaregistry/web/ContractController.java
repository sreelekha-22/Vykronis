package io.vykronis.schemaregistry.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.vykronis.common.json.Json;
import io.vykronis.schemaregistry.registry.SchemaEntry;
import io.vykronis.schemaregistry.registry.SchemaRegistryService;
import io.vykronis.schemaregistry.registry.ValidationReport;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contract registry HTTP boundary. Request/response bodies are handled as raw
 * JSON strings at the edge (the Boot 4 web layer binds with Jackson 3 while the
 * registry and validator use the platform's Jackson-2 based {@code Json}), then
 * parsed and validated via {@link io.vykronis.common.json.Json}.
 */
@RestController
@RequestMapping("/contracts")
public class ContractController {

    public static final MediaType SCHEMA_MEDIA_TYPE = MediaType.parseMediaType("application/schema+json");

    private final SchemaRegistryService service;

    public ContractController(SchemaRegistryService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> summary() {
        return service.summary().stream()
                .map(entry -> Map.<String, Object>of(
                        "topic", entry.topic(),
                        "latestVersion", entry.version(),
                        "registeredAt", entry.registeredAt().toString()))
                .toList();
    }

    @GetMapping("/{topic}/latest")
    public ResponseEntity<Object> latest(@PathVariable String topic) {
        return service.latest(topic)
                .<ResponseEntity<Object>>map(entry -> ResponseEntity.ok()
                        .contentType(SCHEMA_MEDIA_TYPE)
                        .body(entry.schema().toString()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{topic}/version")
    public ResponseEntity<Map<String, Object>> latestVersion(@PathVariable String topic) {
        return service.latest(topic)
                .map(entry -> ResponseEntity.ok(Map.<String, Object>of(
                        "topic", entry.topic(), "version", entry.version())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{topic}/versions/{version}")
    public ResponseEntity<Object> version(@PathVariable String topic, @PathVariable int version) {
        return service.version(topic, version)
                .<ResponseEntity<Object>>map(entry -> ResponseEntity.ok()
                        .contentType(SCHEMA_MEDIA_TYPE)
                        .body(entry.schema().toString()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{topic}")
    public ResponseEntity<Map<String, Object>> register(@PathVariable String topic,
                                                        @RequestBody String body) {
        try {
            SchemaEntry entry = service.register(topic, parseBody(body), Instant.now());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("topic", entry.topic(), "version", entry.version()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{topic}/validate")
    public ResponseEntity<Map<String, Object>> validate(@PathVariable String topic,
                                                        @RequestBody String body) {
        JsonNode payload;
        try {
            payload = parseBody(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "topic", topic, "version", 0, "valid", false, "errors", List.of(e.getMessage())));
        }
        ValidationReport report = service.validate(topic, payload);
        Map<String, Object> result = Map.of(
                "topic", report.topic(),
                "version", report.version(),
                "valid", report.valid(),
                "errors", report.errors());
        return report.valid() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    private static JsonNode parseBody(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("request body must be valid JSON, was empty");
        }
        try {
            return Json.mapper().readTree(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("request body must be valid JSON: " + e.getMessage());
        }
    }
}