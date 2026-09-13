package io.vykronis.schemaregistry.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, monotonic-versioned store of the JSON Schema registered per Kafka
 * topic, plus authoritative payload validation (draft 2020-12 via networknt).
 * Thread-safe: registrations are immutable entries appended under a write lock;
 * validations resolve a schema snapshot and never mutate state.
 */
@Service
public class SchemaRegistryService {

    private final Map<String, List<SchemaEntry>> registry = new ConcurrentHashMap<>();

    public Optional<SchemaEntry> latest(String topic) {
        List<SchemaEntry> versions = registry.get(topic);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(versions.get(versions.size() - 1));
    }

    public Optional<SchemaEntry> version(String topic, int version) {
        if (version < 1) {
            return Optional.empty();
        }
        List<SchemaEntry> versions = registry.get(topic);
        if (versions == null || versions.size() < version) {
            return Optional.empty();
        }
        return Optional.of(versions.get(version - 1));
    }

    public List<SchemaEntry> history(String topic) {
        List<SchemaEntry> versions = registry.get(topic);
        return versions == null ? List.of() : List.copyOf(versions);
    }

    public List<String> topics() {
        return registry.entrySet().stream()
                .map(Map.Entry::getValue)
                .filter(versions -> !versions.isEmpty())
                .map(versions -> versions.get(versions.size() - 1).topic())
                .sorted()
                .toList();
    }

    /**
     * Registers a new immutable version for {@code topic} (1 on first use,
     * otherwise latest + 1). Rejects schemas that are not JSON Schema objects,
     * so garbage can never be published as a contract.
     */
    public SchemaEntry register(String topic, JsonNode schema, Instant registeredAt) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (schema == null || !schema.isObject() || !schema.hasNonNull("$schema")) {
            throw new IllegalArgumentException("schema must be a JSON Schema object (missing or malformed $schema)");
        }
        SchemaEntry entry;
        synchronized (registry) {
            List<SchemaEntry> versions = registry.computeIfAbsent(topic, ignored -> new ArrayList<>());
            entry = new SchemaEntry(topic, versions.size() + 1, schema.deepCopy(), registeredAt);
            versions.add(entry);
        }
        return entry;
    }

    public ValidationReport validate(String topic, JsonNode payload) {
        Optional<SchemaEntry> current = latest(topic);
        if (current.isEmpty()) {
            return ValidationReport.invalid(topic, 0, List.of("no schema registered for topic '" + topic + "'"));
        }
        SchemaEntry entry = current.get();
        Collection<ValidationMessage> violations = schemaOf(entry).validate(payload);
        if (violations.isEmpty()) {
            return ValidationReport.valid(topic, entry.version());
        }
        return ValidationReport.invalid(topic, entry.version(),
                violations.stream().map(Object::toString).sorted().toList());
    }

    /** Latest entry per topic in ascending topic order (used by the summary endpoint). */
    public List<SchemaEntry> summary() {
        return registry.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .filter(versions -> !versions.isEmpty())
                .map(versions -> versions.get(versions.size() - 1))
                .toList();
    }

    private static JsonSchema schemaOf(SchemaEntry entry) {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(entry.schema());
    }
}