package io.vykronis.schemaregistry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps each bundled schema resource (filename stem without {@code .schema.json})
 * in {@code contracts/schema/} to the Kafka topic it governs. Empty by default,
 * so the registry can run without bootstrapping any contracts.
 */
@ConfigurationProperties("vykronis.schema-registry")
public record SchemaRegistryProperties(Map<String, String> bootstrap) {

    public SchemaRegistryProperties {
        bootstrap = bootstrap == null ? new LinkedHashMap<>() : bootstrap;
    }
}