package io.vykronis.incident;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationResult;
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
 * Phase 6 Unit 3 wiring: the incident service produces {@link RemediationCommand}
 * on {@code obs.remediation} once a remediation is approved, and consumes the
 * {@link RemediationResult} the remediation service publishes back — advancing
 * the incident into VERIFYING (or FAILED).
 *
 * <p>Both sides of the topic carry different payload types, so the result
 * reader has its own container factory bean name (the canonical
 * {@code kafkaListenerContainerFactory} already serves {@code obs.alerts}'s
 * {@link IncidentCandidate}). Non-result records (i.e. the commands on the same
 * topic) fail deserialization and arrive as null, which the consumer skips.</p>
 */
@Configuration
public class IncidentRemediationKafkaConfig {

    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, RemediationCommand> remediationCommandProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class);
        props.put("value.serializer", JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(),
                new JsonSerializer<>(Json.mapper()));
    }

    @Bean
    public KafkaTemplate<String, RemediationCommand> remediationCommandKafkaTemplate(
            ProducerFactory<String, RemediationCommand> remediationCommandProducerFactory) {
        return new KafkaTemplate<>(remediationCommandProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, RemediationResult> remediationResultConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "incident-service-remediation");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, RemediationResult.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(RemediationResult.class, Json.mapper(), false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RemediationResult> remediationResultListenerContainerFactory(
            ConsumerFactory<String, RemediationResult> remediationResultConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, RemediationResult> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(remediationResultConsumerFactory);
        return factory;
    }
}