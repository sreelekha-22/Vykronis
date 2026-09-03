package io.vykronis.correlation;

import io.vykronis.contracts.model.Env;

import java.time.Instant;

/**
 * A single metric sample extracted from an {@code obs.metrics} payload. The
 * aggregation window operates on these lightweight values rather than the full
 * {@link io.vykronis.contracts.model.ObservabilityEvent}.
 *
 * @param timestamp   event timestamp
 * @param serviceId   logical service id
 * @param env         environment the sample originated from
 * @param errorCount  number of errors reported in the sample (0 if absent)
 * @param errorRate   error rate reported in the sample (0 if absent, handled by threshold config)
 * @param latencyMs   latency in ms reported in the sample (0 if absent)
 */
public record ObservabilityMetricSample(
        Instant timestamp,
        String serviceId,
        Env env,
        long errorCount,
        double errorRate,
        double latencyMs,
        String traceId
) {
}
