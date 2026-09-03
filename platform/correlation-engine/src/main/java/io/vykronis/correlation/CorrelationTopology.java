package io.vykronis.correlation;

import com.fasterxml.jackson.databind.JsonNode;
import io.vykronis.contracts.model.DeploymentEvent;
import io.vykronis.contracts.model.EventType;
import io.vykronis.contracts.model.IncidentCandidate;
import io.vykronis.contracts.model.ObservabilityEvent;
import io.vykronis.contracts.model.Severity;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Defines the Kafka Streams correlation topology.
 *
 * <p>The pipeline is intentionally small and observable:</p>
 * <ol>
 *   <li>Consume {@code obs.metrics} ({@link ObservabilityEvent}), keep only
 *       METRIC samples.</li>
 *   <li>Window them into tumbling windows and aggregate per service into
 *       {@link WindowStats}.</li>
 *   <li>Emit an {@link Anomaly} when a window breaches the error/latency
 *       threshold.</li>
 *   <li>Attribute the anomaly to a recent deployment via a {@link GlobalKTable}
 *       over {@code obs.deployments}, and emit an {@link IncidentCandidate}
 *       onto {@code obs.alerts}.</li>
 * </ol>
 */
@Configuration
public class CorrelationTopology {

    public static final String METRICS_TOPIC = "obs.metrics";
    public static final String DEPLOYMENTS_TOPIC = "obs.deployments";
    public static final String ALERTS_TOPIC = "obs.alerts";

    private static final String ANOMALY_STORE = "anomaly-window-store";

    private final SerdeFor serde = new SerdeFor();

    @Value("${correlation.window.minutes:1}")
    private long windowMinutes = 1;

    @Value("${correlation.window.grace.seconds:5}")
    private long graceSeconds = 5;

    @Value("${correlation.error-rate-threshold:20.0}")
    private double errorRateThreshold = 20.0;

    @Value("${correlation.error-count-threshold:5}")
    private long errorCountThreshold = 5;

    @Bean
    public Topology correlationTopology(StreamsBuilder builder) {
        GlobalKTable<String, DeploymentEvent> deployments = builder.globalTable(
                DEPLOYMENTS_TOPIC,
                Consumed.with(Serdes.String(), serde.deploymentEvent),
                Materialized.<String, DeploymentEvent,
                        org.apache.kafka.streams.state.KeyValueStore<Bytes, byte[]>>as("deployments-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(serde.deploymentEvent));

        KStream<String, ObservabilityEvent> metrics = builder.stream(
                METRICS_TOPIC,
                Consumed.with(Serdes.String(), serde.observabilityEvent)
                        .withTimestampExtractor(new MetricTimestampExtractor()));

        KStream<String, ObservabilityMetricSample> samples = metrics
                .filter((serviceId, evt) -> evt.type() == EventType.METRIC && evt.payload() != null)
                .mapValues((serviceId, evt) -> toSample(serviceId, evt))
                .filter((serviceId, sample) -> sample != null);

        KGroupedStream<String, ObservabilityMetricSample> grouped = samples.groupByKey(
                Grouped.with(Serdes.String(), serde.sample));

        Duration windowSize = Duration.ofMinutes(windowMinutes);
        Duration grace = Duration.ofSeconds(graceSeconds);

        KStream<String, Anomaly> anomalies = grouped
                .windowedBy(TimeWindows.ofSizeAndGrace(windowSize, grace))
                .aggregate(
                        () -> null,
                        (serviceId, sample, stats) -> stats == null
                                ? WindowStats.initial(serviceId, sample, sample.timestamp(), sample.timestamp().plus(windowSize))
                                : stats.mergeSample(sample),
                        Materialized.<String, WindowStats, WindowStore<Bytes, byte[]>>as(ANOMALY_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(serde.windowStats))
                .toStream()
                .flatMap((windowedKey, stats) -> {
                    if (stats == null || stats.sampleCount() == 0) {
                        return java.util.Collections.emptyList();
                    }
                    if (!breachedThreshold(stats)) {
                        return java.util.Collections.emptyList();
                    }
                    return java.util.Collections.singletonList(
                            new org.apache.kafka.streams.KeyValue<>(
                                    windowedKey.key(),
                                    new Anomaly(
                                            windowedKey.key(),
                                            stats.env(),
                                            windowedKey.window().startTime(),
                                            windowedKey.window().endTime(),
                                            stats,
                                            buildReason(stats),
                                            severityFor(stats).name())));
                });

        KStream<String, IncidentCandidate> candidates = anomalies.leftJoin(
                deployments,
                (serviceId, anomaly) -> serviceId,
                (anomaly, deployment) -> toCandidate(anomaly, deployment));

        candidates.to(ALERTS_TOPIC, Produced.with(Serdes.String(), serde.incidentCandidate));

        return builder.build();
    }

    private IncidentCandidate toCandidate(Anomaly anomaly, DeploymentEvent deployment) {
        JsonNode metrics = io.vykronis.common.json.Json.mapper().createObjectNode()
                .put("error_rate_max", anomaly.stats().errorRateMax())
                .put("error_rate_min", anomaly.stats().errorRateMin())
                .put("error_count", anomaly.stats().errorCount())
                .put("sample_count", anomaly.stats().sampleCount())
                .put("latency_min_ms", anomaly.stats().latencyMinMs())
                .put("latency_max_ms", anomaly.stats().latencyMaxMs());
        return new IncidentCandidate(
                UUID.randomUUID(),
                Instant.now(),
                anomaly.serviceId(),
                anomaly.env(),
                severityFor(anomaly.stats()),
                anomaly.reason(),
                anomaly.windowStart(),
                anomaly.windowEnd(),
                metrics,
                deployment == null ? null : deployment.deploymentId().toString());
    }

    private ObservabilityMetricSample toSample(String serviceId, ObservabilityEvent evt) {
        JsonNode p = evt.payload();
        long errorCount = p.has("error_count") ? p.get("error_count").asLong(0) : 0;
        double errorRate = p.has("error_rate") ? p.get("error_rate").asDouble(0.0) : 0.0;
        double latency = p.has("latency_ms") ? p.get("latency_ms").asDouble(0.0) : 0.0;
        return new ObservabilityMetricSample(evt.timestamp(), serviceId, evt.env(), errorCount, errorRate, latency, evt.traceId());
    }

    private boolean breachedThreshold(WindowStats stats) {
        return stats.errorRateMax() >= errorRateThreshold || stats.errorCount() >= errorCountThreshold;
    }

    private String buildReason(WindowStats stats) {
        if (stats.errorRateMax() >= errorRateThreshold) {
            return "error_rate " + stats.errorRateMax() + " >= " + errorRateThreshold;
        }
        return "error_count " + stats.errorCount() + " >= " + errorCountThreshold;
    }

    private Severity severityFor(WindowStats stats) {
        double maxRate = stats.errorRateMax();
        if (maxRate >= 60) {
            return Severity.CRITICAL;
        }
        if (maxRate >= 40) {
            return Severity.HIGH;
        }
        if (maxRate >= 20) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }

    private static final class SerdeFor {
        final org.apache.kafka.common.serialization.Serde<ObservabilityEvent> observabilityEvent =
                ContractSerdes.json(ObservabilityEvent.class);
        final org.apache.kafka.common.serialization.Serde<ObservabilityMetricSample> sample =
                ContractSerdes.json(ObservabilityMetricSample.class);
        final org.apache.kafka.common.serialization.Serde<WindowStats> windowStats =
                ContractSerdes.json(WindowStats.class);
        final org.apache.kafka.common.serialization.Serde<DeploymentEvent> deploymentEvent =
                ContractSerdes.json(DeploymentEvent.class);
        final org.apache.kafka.common.serialization.Serde<IncidentCandidate> incidentCandidate =
                ContractSerdes.json(IncidentCandidate.class);
    }
}
