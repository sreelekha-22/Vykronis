package io.vykronis.orchestrator.demo;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.JfrRecord;
import io.vykronis.contracts.model.ObservabilityEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Producer wiring for the demo traffic generator. Metrics go out as real JSON
 * {@link ObservabilityEvent}s (so the correlation engine can deserialize them),
 * deployment records are marshalled to a JSON string by
 * {@link DeploymentService}, and JFR chunks stream out as typed
 * {@link JfrRecord}s keyed by service.
 */
@Configuration
public class DemoKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, ObservabilityEvent> demoProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, ObservabilityEvent> observabilityKafkaTemplate() {
        return new KafkaTemplate<>(demoProducerFactory());
    }

    @Bean
    public ProducerFactory<String, JfrRecord> jfrProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new DefaultKafkaProducerFactory<>(
                config, new StringSerializer(), new JsonSerializer<>(Json.mapper()));
    }

    @Bean
    public KafkaTemplate<String, JfrRecord> jfrKafkaTemplate() {
        return new KafkaTemplate<>(jfrProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(config);
        return new KafkaTemplate<>(factory);
    }
}