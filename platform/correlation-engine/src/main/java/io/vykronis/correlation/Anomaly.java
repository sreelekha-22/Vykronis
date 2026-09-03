package io.vykronis.correlation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vykronis.contracts.model.Env;

import java.time.Instant;

/**
 * A detected anomaly: a window where the service's error/latency statistics
 * breached the configured threshold. These are matched against nearby
 * deployments to attribute a cause, then emitted as
 * {@link io.vykronis.contracts.model.IncidentCandidate} onto {@code obs.alerts}.
 *
 * @param serviceId   the affected service
 * @param env         environment the anomaly was observed in
 * @param windowStart start of the anomalous window
 * @param windowEnd   end of the anomalous window
 * @param stats       aggregated statistics over the window
 * @param reason      human/machine-readable description of the breach
 * @param severityKey derived severity bucket as a string
 */
public record Anomaly(
        String serviceId,
        Env env,
        @JsonProperty("windowStart") Instant windowStart,
        @JsonProperty("windowEnd") Instant windowEnd,
        WindowStats stats,
        String reason,
        String severityKey
) {
}
