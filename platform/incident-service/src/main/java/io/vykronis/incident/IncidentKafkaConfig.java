package io.vykronis.incident;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.IncidentCandidate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for the incident service. Listens on
 * {@code obs.alerts} for {@link IncidentCandidate} records. Uses the shared
 * {@link Json#mapper()} so Instants and JsonNode fields deserialize
 * consistently with the correlation engine.
 */
@Configuration
public class IncidentKafkaConfig {

    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:incident-service}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, IncidentCandidate> candidateConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, IncidentCandidate.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props, new org.apache.kafka.common.serialization.StringDeserializer(),
                new JsonDeserializer<>(IncidentCandidate.class, Json.mapper(), false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, IncidentCandidate> incidentKafkaListenerContainerFactory(
            ConsumerFactory<String, IncidentCandidate> candidateConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, IncidentCandidate> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(candidateConsumerFactory);
        return factory;
    }
}
