package io.vykronis.schemaregistry.registry;

import io.vykronis.common.json.Json;
import io.vykronis.schemaregistry.config.SchemaRegistryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;

/**
 * Seeds the registry from the bundled contract schemas in the {@code contracts}
 * dependency ({@code classpath:contracts/schema/*.schema.json}). Topics that
 * must already exist in Kafka (obs.metrics etc.) are auto-created by the chart's
 * kafka-topics-init initContainer; registration here is independent of Kafka.
 */
@Component
public class RegistryBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegistryBootstrap.class);

    private final SchemaRegistryService registry;
    private final SchemaRegistryProperties properties;

    public RegistryBootstrap(SchemaRegistryService registry, SchemaRegistryProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, String> bootstrap = properties.bootstrap();
        if (bootstrap.isEmpty()) {
            log.info("No bootstrap contract schemas configured; registry starts empty");
        }
        bootstrap.forEach((resource, topic) -> {
            String path = "schema/" + resource + ".schema.json";
            try (InputStream in = new ClassPathResource(path).getInputStream()) {
                var schema = Json.mapper().readTree(in);
                SchemaEntry entry = registry.register(topic, schema, Instant.now());
                log.info("Bootstrapped contract {} -> {} (version {})", resource, topic, entry.version());
            } catch (Exception e) {
                log.error("Cannot bootstrap contract {} from {}: {}", resource, path, e.getMessage());
            }
        });
    }
}