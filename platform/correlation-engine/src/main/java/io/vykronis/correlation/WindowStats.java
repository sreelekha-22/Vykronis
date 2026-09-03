package io.vykronis.correlation;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Aggregated metric statistics for a service over a window. Produced by the
 * windowed aggregation and consumed by the anomaly detector. Stored in the
 * Kafka Streams state store as JSON.
 *
 * @param serviceId   the service the window covers
 * @param sampleCount number of metric samples aggregated
 * @param errorCount  cumulative error count across samples
 * @param errorRateMin observed minimum error_rate sample
 * @param errorRateMax observed maximum error_rate sample
 * @param latencyMinMs observed minimum latency sample (ms)
 * @param latencyMaxMs observed maximum latency sample (ms)
 */
public record WindowStats(
        String serviceId,
        long sampleCount,
        long errorCount,
        double errorRateMin,
        double errorRateMax,
        double latencyMinMs,
        double latencyMaxMs,
        @JsonProperty("windowStart") Instant windowStart,
        @JsonProperty("windowEnd") Instant windowEnd
) {
    public WindowStats mergeSample(ObservabilityMetricSample sample) {
        double er = sample.errorRate();
        double lat = sample.latencyMs();
        return new WindowStats(
                serviceId,
                sampleCount + 1,
                errorCount + sample.errorCount(),
                Math.min(errorRateMin, er),
                Math.max(errorRateMax, er),
                Math.min(latencyMinMs, lat),
                Math.max(latencyMaxMs, lat),
                windowStart,
                windowEnd
        );
    }

    public static WindowStats initial(String serviceId, ObservabilityMetricSample sample,
                                      Instant windowStart, Instant windowEnd) {
        return new WindowStats(
                serviceId,
                1,
                sample.errorCount(),
                sample.errorRate(),
                sample.errorRate(),
                sample.latencyMs(),
                sample.latencyMs(),
                windowStart,
                windowEnd
        );
    }

    public static WindowStats empty(String serviceId, Instant windowStart, Instant windowEnd) {
        return new WindowStats(
                serviceId,
                0,
                0,
                Double.MAX_VALUE,
                Double.MIN_VALUE,
                Double.MAX_VALUE,
                Double.MIN_VALUE,
                windowStart,
                windowEnd
        );
    }
}
