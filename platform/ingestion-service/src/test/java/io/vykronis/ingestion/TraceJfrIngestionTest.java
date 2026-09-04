package io.vykronis.ingestion;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.JfrRecord;
import io.vykronis.contracts.model.TraceSpan;
import io.vykronis.contracts.model.TraceStatus;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class TraceJfrIngestionTest {

    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:3.9.0");
    private static final int HOST_PLAINTEXT = 19092;
    private static final int HOST_BROKER = 19093;
    private static final int HOST_CONTROLLER = 19094;

    @Container
    static GenericContainer<?> kafka = buildKafka();

    private static GenericContainer<?> buildKafka() {
        GenericContainer<?> container = new GenericContainer<>(KAFKA_IMAGE)
                .withExposedPorts(9092, 9093, 9094)
                .withEnv(Map.ofEntries(
                        Map.entry("CLUSTER_ID", "4L6g3nShT-eMCtK--X86sw"),
                        Map.entry("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,BROKER://0.0.0.0:9093,CONTROLLER://0.0.0.0:9094"),
                        Map.entry("KAFKA_ADVERTISED_LISTENERS",
                                "PLAINTEXT://localhost:" + HOST_PLAINTEXT
                                        + ",BROKER://localhost:9093"
                                        + ",CONTROLLER://localhost:9094"),
                        Map.entry("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                                "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT"),
                        Map.entry("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER"),
                        Map.entry("KAFKA_PROCESS_ROLES", "broker,controller"),
                        Map.entry("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER"),
                        Map.entry("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9094"),
                        Map.entry("KAFKA_NODE_ID", "1"),
                        Map.entry("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1"),
                        Map.entry("KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS", "1"),
                        Map.entry("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1"),
                        Map.entry("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")))
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(180)));
        container.setPortBindings(List.of(
                HOST_PLAINTEXT + ":9092",
                HOST_BROKER + ":9093",
                HOST_CONTROLLER + ":9094"));
        return container;
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("kafka.bootstrap-servers", () -> "localhost:" + HOST_PLAINTEXT);
    }

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return RestClient.create("http://localhost:" + port);
    }

@Test
void traceSpanPersistsToObsTracesKeyedByTraceId() {
    TraceSpan span = new TraceSpan(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "trace-123",
            "payment-service",
            Env.PROD,
            "checkout",
            Instant.parse("2026-09-04T10:00:00Z"),
            42L,
            TraceStatus.OK,
            null,
            Map.of("http.method", "POST"));

    ResponseEntity<Map> response = restClient().post()
            .uri("/api/ingest/traces")
            .contentType(MediaType.APPLICATION_JSON)
            .body(span)
            .retrieve()
            .toEntity(Map.class);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(response.getBody()).containsEntry("id", span.id().toString());

    ConsumerRecord<String, Object> record = pollSingle(Topics.TRACES);
    assertThat(record.key()).isEqualTo("trace-123");
    assertThat(record.value()).isEqualTo(span);
}

@Test
void jfrRecordPersistsToObsJfrKeyedByServiceId() {
    JfrRecord jfr = new JfrRecord(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "demo-service",
            Env.DEV,
            "alloc-chunk-1.jfr",
            new byte[]{0x01, 0x02, 0x03, 0x04, 0x05},
            Instant.parse("2026-09-04T10:00:00Z"));

    ResponseEntity<Map> response = restClient().post()
            .uri("/api/ingest/jfr")
            .contentType(MediaType.APPLICATION_JSON)
            .body(jfr)
            .retrieve()
            .toEntity(Map.class);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(response.getBody()).containsEntry("id", jfr.id().toString());

    ConsumerRecord<String, Object> record = pollSingle(Topics.JFR);
    assertThat(record.key()).isEqualTo("demo-service");
    assertThat(record.value()).isEqualTo(jfr);
}

    private ConsumerRecord<String, Object> pollSingle(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + HOST_PLAINTEXT);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ingestion-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        JsonDeserializer<Object> valueDeserializer = new JsonDeserializer<>(Json.mapper());
        valueDeserializer.configure(
                Map.of("spring.json.trusted.packages", "io.vykronis.contracts.model"), false);
        try (Consumer<String, Object> consumer = new KafkaConsumer<>(
                props, new StringDeserializer(), valueDeserializer)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
            while (Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(500));
                if (!records.isEmpty()) {
                    return records.iterator().next();
                }
            }
            throw new AssertionError("no record received on " + topic + " within 15s");
        }
    }
}