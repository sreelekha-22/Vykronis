package io.vykronis.correlation;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Topology test using {@link TopologyTestDriver}: feeds error-burst metric
 * events and a deployment into the topology and verifies an
 * {@link IncidentCandidate} is emitted onto {@code obs.alerts}.
 */
class CorrelationTopologyTest {

    @Test
    void faultyDeploymentYieldsIncidentCandidate() {
        StreamsBuilder builder = new StreamsBuilder();
        CorrelationTopology topologyConfig = new CorrelationTopology();
        Topology topology = topologyConfig.correlationTopology(builder);

        Serde<String> stringSerde = Serdes.String();
        Serializer<String> stringSer = stringSerde.serializer();
        Deserializer<String> stringDeser = stringSerde.deserializer();
        Serde<ObservabilityEvent> eventSerde = ContractSerdes.json(ObservabilityEvent.class);
        Serde<DeploymentEvent> deploySerde = ContractSerdes.json(DeploymentEvent.class);
        Serde<IncidentCandidate> candidateSerde = ContractSerdes.json(IncidentCandidate.class);

        try (TopologyTestDriver driver = new TopologyTestDriver(topology)) {
            TestInputTopic<String, ObservabilityEvent> metrics =
                    driver.createInputTopic(CorrelationTopology.METRICS_TOPIC, stringSer, eventSerde.serializer());
            TestInputTopic<String, DeploymentEvent> deployments =
                    driver.createInputTopic(CorrelationTopology.DEPLOYMENTS_TOPIC, stringSer, deploySerde.serializer());
            TestOutputTopic<String, IncidentCandidate> alerts =
                    driver.createOutputTopic(CorrelationTopology.ALERTS_TOPIC, stringDeser, candidateSerde.deserializer());

            // A faulty deployment for payment-service.
            DeploymentEvent deploy = new DeploymentEvent(
                    UUID.randomUUID(), "payment-service", "1.4.2", Env.PROD,
                    Instant.parse("2026-09-03T10:00:00Z"), Instant.parse("2026-09-03T10:00:30Z"),
                    DeploymentStatus.SUCCESS, "ci-agent");
            deployments.pipeInput("payment-service", deploy);

            // Error burst: several high-error-rate metric samples in the window.
            Instant t0 = Instant.parse("2026-09-03T10:01:00Z");
            for (int i = 0; i < 8; i++) {
                metrics.pipeInput("payment-service", metricEvent("payment-service",
                        t0.plusSeconds(i * 5), 45.0, 5, 800.0));
            }

            // Advance time to close the window.
            driver.advanceWallClockTime(java.time.Duration.ofMinutes(2));
            metrics.pipeInput("payment-service", metricEvent("other-service",
                    Instant.parse("2026-09-03T10:03:00Z"), 1.0, 0, 5.0));

            var candidates = alerts.readValuesToList();
            assertThat(candidates).isNotEmpty();
            IncidentCandidate candidate = candidates.get(0);
            assertThat(candidate.serviceId()).isEqualTo("payment-service");
            assertThat(candidate.deploymentId()).isEqualTo(deploy.deploymentId().toString());
            assertThat(candidate.metrics().get("error_rate_max").asDouble()).isGreaterThanOrEqualTo(45.0);
        }
    }

    private ObservabilityEvent metricEvent(String serviceId, Instant ts, double errorRate,
                                           long errorCount, double latencyMs) {
        JsonNode payload = io.vykronis.common.json.Json.mapper().createObjectNode()
                .put("error_rate", errorRate)
                .put("error_count", errorCount)
                .put("latency_ms", latencyMs);
        return new ObservabilityEvent(UUID.randomUUID(), ts, "generator", serviceId,
                Env.PROD, EventType.METRIC, payload, null);
    }
}
