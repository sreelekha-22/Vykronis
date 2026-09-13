package io.vykronis.schemaregistry;

import io.vykronis.schemaregistry.config.SchemaRegistryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SchemaRegistryProperties.class)
public class SchemaRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchemaRegistryApplication.class, args);
    }
}