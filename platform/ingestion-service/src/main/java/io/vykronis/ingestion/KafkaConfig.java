package io.vykronis.ingestion;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.JfrRecord;
import io.vykronis.contracts.model.ObservabilityEvent;
import io.vykronis.contracts.model.TraceSpan;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private static final String BOOTSTRAP_SERVERS = "${kafka.bootstrap-servers:localhost:9092}";

    @Bean
    public ProducerFactory<String, ObservabilityEvent> observabilityEventProducerFactory(
            @Value(BOOTSTRAP_SERVERS) String bootstrapServers) {
        return producerFactory(bootstrapServers);
    }

    @Bean
    public KafkaTemplate<String, ObservabilityEvent> observabilityEventKafkaTemplate(
            ProducerFactory<String, ObservabilityEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<String, TraceSpan> traceSpanProducerFactory(
            @Value(BOOTSTRAP_SERVERS) String bootstrapServers) {
        return producerFactory(bootstrapServers);
    }

    @Bean
    public KafkaTemplate<String, TraceSpan> traceSpanKafkaTemplate(
            ProducerFactory<String, TraceSpan> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<String, JfrRecord> jfrRecordProducerFactory(
            @Value(BOOTSTRAP_SERVERS) String bootstrapServers) {
        return producerFactory(bootstrapServers);
    }

    @Bean
    public KafkaTemplate<String, JfrRecord> jfrRecordKafkaTemplate(
            ProducerFactory<String, JfrRecord> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    private <T> ProducerFactory<String, T> producerFactory(String bootstrapServers) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new DefaultKafkaProducerFactory<>(
                configs,
                new StringSerializer(),
                new JsonSerializer<>(Json.mapper()));
    }
}