package io.vykronis.schemaregistry;

import io.vykronis.schemaregistry.registry.SchemaEntry;
import io.vykronis.schemaregistry.registry.SchemaRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchemaRegistryApplicationTests {

    @Autowired
    private SchemaRegistryService registry;

    @Test
    void contextLoads() {
        // Context is wired: main class, properties binder, bootstrap runner.
    }

    @Test
    void bootsAllFiveContractSchemas() {
        List<String> topics = registry.topics();
        assertThat(topics)
                .containsExactly("obs.alerts", "obs.deployments", "obs.jfr", "obs.metrics", "obs.remediation");

        assertThat(registry.latest("obs.remediation")).isPresent();
        SchemaEntry metrics = registry.latest("obs.metrics").orElseThrow();
        assertThat(metrics.version()).isEqualTo(1);
        assertThat(metrics.schema().has("$schema")).isTrue();

        // New versions never replace history: registering again yields version 2.
        var second = registry.register("obs.metrics", metrics.schema(), java.time.Instant.now());
        assertThat(second.version()).isEqualTo(2);
        assertThat(registry.history("obs.metrics")).hasSize(2);
        assertThat(registry.latest("obs.metrics").orElseThrow().version()).isEqualTo(2);
    }
}