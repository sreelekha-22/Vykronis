package io.vykronis.correlation;

import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Streams configuration for the correlation engine.
 *
 * <p>State stores live in {@code ./streams-state} so we can see intermediate
 * stores on disk during local development. We bind on {@code localhost} with
 * {@code AT_LEAST_ONCE} for the Phase 2 north-star path; exactly-once can be
 * enabled later once the whole loop is verified.</p>
 */
@Configuration
public class CorrelationStreamsConfig {

    @Value("${spring.application.name:correlation-engine}")
    private String appId;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfiguration(
            @Value("${kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                org.apache.kafka.common.serialization.Serdes.String().getClass().getName());
        props.put(StreamsConfig.STATE_DIR_CONFIG, "./streams-state");
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10_000);
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        return new KafkaStreamsConfiguration(props);
    }
}
