package io.vykronis.remediation;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationResult;
import io.vykronis.contracts.Topics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring for remediation: consumes {@link RemediationCommand} records
 * from {@code obs.remediation} (issued by the incident service after policy
 * approval) and publishes {@link RemediationResult} reports back to the same
 * topic. Uses the shared {@link Json#mapper()} so Instants and enums
 * round-trip consistently with the rest of the platform.
 */
@Configuration
public class RemediationKafkaConfig {

    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:remediation-service}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, RemediationCommand> remediationCommandConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, RemediationCommand.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(RemediationCommand.class, Json.mapper(), false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RemediationCommand> kafkaListenerContainerFactory(
            ConsumerFactory<String, RemediationCommand> remediationCommandConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, RemediationCommand> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(remediationCommandConsumerFactory);
        return factory;
    }

    @Bean
    public ProducerFactory<String, RemediationResult> remediationResultProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class);
        props.put("value.serializer", JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(),
                new JsonSerializer<>(Json.mapper()));
    }

    @Bean
    public KafkaTemplate<String, RemediationResult> remediationResultKafkaTemplate(
            ProducerFactory<String, RemediationResult> remediationResultProducerFactory) {
        return new KafkaTemplate<>(remediationResultProducerFactory);
    }
}