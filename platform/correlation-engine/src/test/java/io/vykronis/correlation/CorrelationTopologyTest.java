package io.vykronis.correlation;

import com.fasterxml.jackson.databind.JsonNode;
import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.DeploymentEvent;
import io.vykronis.contracts.model.DeploymentStatus;
import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.EventType;
import io.vykronis.contracts.model.IncidentCandidate;
import io.vykronis.contracts.model.ObservabilityEvent;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Topology tests using {@link TopologyTestDriver}. Each test drives fresh
 * windows so threshold, join, and edge cases are exercised in isolation.
 */
class CorrelationTopologyTest {

    // ---- happy path -----------------------------------------------------

    @Test
    void faultyDeploymentYieldsIncidentCandidate() {
        try (Harness h = new Harness()) {
            h.deploy("payment-service", "1.4.2", "2026-09-03T10:00:00Z");
            hintErrorBurst(h, "payment-service", "2026-09-03T10:01:00Z", 8, 45.0, 5);
            h.advanceAndWarm("2026-09-03T10:03:00Z");

            var candidates = h.alerts().readValuesToList();
            assertThat(candidates).isNotEmpty();
            IncidentCandidate c = candidates.get(0);
            assertThat(c.serviceId()).isEqualTo("payment-service");
            assertThat(c.deploymentId()).isNotBlank();
            assertThat(c.metrics().get("error_rate_max").asDouble()).isGreaterThanOrEqualTo(45.0);
            assertThat(c.severity().name()).isEqualTo("HIGH");
        }
    }

    // ---- negative / edge cases -----------------------------------------

    @Test
    void healthyTrafficDoesNotEmitCandidate() {
        try (Harness h = new Harness()) {
            Instant t0 = Instant.parse("2026-09-03T10:01:00Z");
            for (int i = 0; i < 8; i++) {
                h.metrics().pipeInput("order-service", metricEvent("order-service", t0.plusSeconds(i * 5), 2.0, 0, 50.0));
            }
            h.advanceAndWarm("2026-09-03T10:03:00Z");

            assertThat(h.alerts().readValuesToList()).isEmpty();
        }
    }

    @Test
    void errorCountThresholdCanBreachEvenWhenRateLow() {
        try (Harness h = new Harness()) {
            Instant t0 = Instant.parse("2026-09-03T10:01:00Z");
            // Low error_rate (5%) but high cumulative error_count across samples.
            for (int i = 0; i < 8; i++) {
                h.metrics().pipeInput("payment-service",
                        metricEvent("payment-service", t0.plusSeconds(i * 5), 5.0, 2, 60.0));
            }
            h.advanceAndWarm("2026-09-03T10:03:00Z");

            var candidates = h.alerts().readValuesToList();
            // 8 samples * error_count 2 = 16 >= 5 threshold => breach via count.
            assertThat(candidates).isNotEmpty();
            assertThat(candidates.get(0).serviceId()).isEqualTo("payment-service");
        }
    }

    @Test
    void onlyBreachingServiceEmitsCandidate() {
        try (Harness h = new Harness()) {
            Instant t0 = Instant.parse("2026-09-03T10:01:00Z");
            for (int i = 0; i < 6; i++) {
                h.metrics().pipeInput("payment-service", metricEvent("payment-service", t0.plusSeconds(i * 5), 50.0, 6, 900.0));
                h.metrics().pipeInput("order-service", metricEvent("order-service", t0.plusSeconds(i * 5), 1.0, 0, 40.0));
                h.metrics().pipeInput("inventory-service", metricEvent("inventory-service", t0.plusSeconds(i * 5), 2.0, 0, 45.0));
            }
            h.advanceAndWarm("2026-09-03T10:03:00Z");

            var candidates = h.alerts().readValuesToList();
            assertThat(candidates).isNotEmpty();
            assertThat(candidates).allMatch(c -> c.serviceId().equals("payment-service"));
        }
    }

    @Test
    void metricWithoutErrorFieldsDoesNotBreach() {
        try (Harness h = new Harness()) {
            Instant t0 = Instant.parse("2026-09-03T10:01:00Z");
            JsonNode payload = Json.mapper().createObjectNode().put("only_a_cpu", 12.0);
            for (int i = 0; i < 8; i++) {
                h.metrics().pipeInput("payment-service",
                        new ObservabilityEvent(UUID.randomUUID(), t0.plusSeconds(i * 5), "gen",
                                "payment-service", Env.PROD, EventType.METRIC, payload, null));
            }
            h.advanceAndWarm("2026-09-03T10:03:00Z");
            assertThat(h.alerts().readValuesToList()).isEmpty();
        }
    }

    @Test
    void nonMetricEventsAreIgnored() {
        try (Harness h = new Harness()) {
            Instant t0 = Instant.parse("2026-09-03T10:01:00Z");
            JsonNode payload = Json.mapper().createObjectNode().put("error_rate", 99.0).put("error_count", 100);
            for (int i = 0; i < 5; i++) {
                // LOG event with high error fields must be ignored (only METRIC is aggregated).
                h.metrics().pipeInput("payment-service",
                        new ObservabilityEvent(UUID.randomUUID(), t0.plusSeconds(i * 5), "gen",
                                "payment-service", Env.PROD, EventType.LOG, payload, null));
            }
            h.advanceAndWarm("2026-09-03T10:03:00Z");
            assertThat(h.alerts().readValuesToList()).isEmpty();
        }
    }

    // ---- helpers --------------------------------------------------------

    private void hintErrorBurst(Harness h, String serviceId, String startIso, int count, double errorRate, int errCount) {
        Instant t0 = Instant.parse(startIso);
        for (int i = 0; i < count; i++) {
            h.metrics().pipeInput(serviceId, metricEvent(serviceId, t0.plusSeconds(i * 5), errorRate, errCount, 800.0));
        }
    }

    private static ObservabilityEvent metricEvent(String serviceId, Instant ts, double errorRate,
                                                  long errorCount, double latencyMs) {
        JsonNode payload = Json.mapper().createObjectNode()
                .put("error_rate", errorRate)
                .put("error_count", errorCount)
                .put("latency_ms", latencyMs);
        return new ObservabilityEvent(UUID.randomUUID(), ts, "generator", serviceId,
                Env.PROD, EventType.METRIC, payload, null);
    }

    private static final class Harness implements AutoCloseable {
        private final TopologyTestDriver driver;
        private final TestInputTopic<String, ObservabilityEvent> metricsTopic;
        private final TestInputTopic<String, DeploymentEvent> deployTopic;
        private final TestOutputTopic<String, IncidentCandidate> alertsTopic;

        Harness() {
            StreamsBuilder builder = new StreamsBuilder();
            CorrelationTopology config = new CorrelationTopology();
            Topology topology = config.topology(builder);

            Serde<String> stringSerde = Serdes.String();
            Serializer<String> stringSer = stringSerde.serializer();
            Deserializer<String> stringDeser = stringSerde.deserializer();
            Serde<ObservabilityEvent> eventSerde = ContractSerdes.json(ObservabilityEvent.class);
            Serde<DeploymentEvent> deploySerde = ContractSerdes.json(DeploymentEvent.class);
            Serde<IncidentCandidate> candidateSerde = ContractSerdes.json(IncidentCandidate.class);

            driver = new TopologyTestDriver(topology);
            metricsTopic = driver.createInputTopic(CorrelationTopology.METRICS_TOPIC, stringSer, eventSerde.serializer());
            deployTopic = driver.createInputTopic(CorrelationTopology.DEPLOYMENTS_TOPIC, stringSer, deploySerde.serializer());
            alertsTopic = driver.createOutputTopic(CorrelationTopology.ALERTS_TOPIC, stringDeser, candidateSerde.deserializer());
        }

        void deploy(String serviceId, String version, String startIso) {
            deployTopic.pipeInput(serviceId, new DeploymentEvent(
                    UUID.randomUUID(), serviceId, version, Env.PROD,
                    Instant.parse(startIso), Instant.parse(startIso), DeploymentStatus.SUCCESS, "ci-agent"));
        }

        TestInputTopic<String, ObservabilityEvent> metrics() {
            return metricsTopic;
        }

        TestOutputTopic<String, IncidentCandidate> alerts() {
            return alertsTopic;
        }

        void advanceAndWarm(String wakeIso) {
            driver.advanceWallClockTime(Duration.ofMinutes(2));
            metricsTopic.pipeInput("__warm__", metricEvent("__warm__", Instant.parse(wakeIso), 1.0, 0, 5.0));
        }

        @Override
        public void close() {
            driver.close();
        }
    }
}
