package io.vykronis.schemaregistry.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaRegistryServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SchemaRegistryService service;

    @BeforeEach
    void setUp() {
        service = new SchemaRegistryService();
    }

    private JsonNode schema(String id) {
        ObjectNode node = mapper.createObjectNode();
        node.put("$schema", "https://json-schema.org/draft/2020-12/schema")
                .put("$id", "test." + id + ".schema.json")
                .set("type", mapper.getNodeFactory().textNode("object"));
        return node;
    }

    @Test
    void unknownTopicHasNoLatestVersionOrHistory() {
        assertThat(service.latest("obs.unknown")).isEmpty();
        assertThat(service.version("obs.unknown", 1)).isEmpty();
        assertThat(service.history("obs.unknown")).isEmpty();
        assertThat(service.topics()).isEmpty();
        assertThat(service.summary()).isEmpty();
    }

    @Test
    void registersMonotonicVersionsPerTopic() {
        var v1 = service.register("obs.metrics", schema("m1"), Instant.now());
        var v2 = service.register("obs.metrics", schema("m2"), Instant.now());
        var other = service.register("obs.alerts", schema("a1"), Instant.now());

        assertThat(v1.version()).isEqualTo(1);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(other.version()).isEqualTo(1);

        assertThat(service.latest("obs.metrics").orElseThrow().version()).isEqualTo(2);
        assertThat(service.version("obs.metrics", 1).orElseThrow().schema().get("$id").asText())
                .isEqualTo("test.m1.schema.json");
        assertThat(service.history("obs.metrics")).hasSize(2);
    }

    @Test
    void missingVersionBelowOneOrBeyondHistoryIsEmpty() {
        service.register("t", schema("x"), Instant.now());
        assertThat(service.version("t", 0)).isEmpty();
        assertThat(service.version("t", 3)).isEmpty();
    }

    @Test
    void sortsTopicsShowsLatestOnlyAndKeepsHistoriesImmutable() {
        service.register("obs.zeta", schema("z"), Instant.now());
        service.register("obs.alpha", schema("a"), Instant.now());
        service.register("obs.alpha", schema("a2"), Instant.now());

        assertThat(service.topics()).containsExactly("obs.alpha", "obs.zeta");
        assertThat(service.summary()).extracting(SchemaEntry::topic)
                .containsExactly("obs.alpha", "obs.zeta");
        assertThat(service.summary()).extracting(SchemaEntry::version)
                .containsExactly(2, 1);

        List<SchemaEntry> history = service.history("obs.alpha");
        assertThatThrownBy(history::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(service.history("obs.alpha")).hasSize(2);
    }

    @Test
    void rejectsBlankTopicsNonObjectSchemasAndMissingSchemaMarker() {
        assertThatThrownBy(() -> service.register("  ", schema("x"), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
        assertThatThrownBy(() -> service.register("t", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema");
        assertThatThrownBy(() -> service.register("t", mapper.createArrayNode(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema");
        assertThatThrownBy(() -> service.register("t", mapper.createObjectNode(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$schema");
    }

    @Test
    void storedSchemaIsACopyNotTheCallersReference() {
        JsonNode original = schema("copy");
        ((ObjectNode) original).put("extra", true);
        service.register("t", original, Instant.now());
        ((ObjectNode) original).remove("extra");
        assertThat(service.latest("t").orElseThrow().schema().has("extra")).isTrue();
    }

    @Test
    void validatesPayloadsAgainstTheLatestSchema() throws Exception {
        JsonNode metricSchema = mapper.readTree("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["id"],
                  "properties": { "id": { "type": "string", "format": "uuid" } }
                }
                """);
        service.register("obs.metrics", metricSchema, Instant.now());

        JsonNode good = mapper.readTree("{\"id\": \"550e8400-e29b-41d4-a716-446655440000\"}");
        ValidationReport ok = service.validate("obs.metrics", good);
        assertThat(ok.valid()).isTrue();
        assertThat(ok.version()).isEqualTo(1);
        assertThat(ok.errors()).isEmpty();

        JsonNode bad = mapper.readTree("{\"id\": \"not-an-uuid\", \"stray\": 1}");
        ValidationReport nok = service.validate("obs.metrics", bad);
        assertThat(nok.valid()).isFalse();
        assertThat(nok.errors()).isNotEmpty();
        assertThat(nok.errors()).anyMatch(message -> message.contains("stray"));
    }

    @Test
    void validatingAnUnregisteredTopicReportsATopicalError() {
        ValidationReport report = service.validate("obs.missing", mapper.createObjectNode());
        assertThat(report.valid()).isFalse();
        assertThat(report.version()).isZero();
        assertThat(report.errors()).singleElement().satisfies(
                message -> assertThat(message).contains("no schema registered"));
    }
}