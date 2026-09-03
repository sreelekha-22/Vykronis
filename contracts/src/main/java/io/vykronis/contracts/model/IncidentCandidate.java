package io.vykronis.contracts.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * A candidate incident produced by the correlation engine once it detects a
 * significant deviation (e.g. an error-rate or latency spike) that warrants
 * opening or updating an incident.
 *
 * <p>Emitted on the {@code obs.alerts} topic. The incident service consumes
 * these to {@code OPEN} (new) or {@code UPDATE} (existing) an incident. The
 * correlation-engine key on {@code serviceId} so all candidates for a service
 * land in the same partition and stay ordered.</p>
 *
 * @param candidateId unique candidate id (for idempotent open/update)
 * @param timestamp   when the correlation decision was made
 * @param serviceId   logical service id (partition key)
 * @param env         environment the anomaly was observed in
 * @param severity    suggested severity (LOW / MEDIUM / HIGH / CRITICAL)
 * @param reason      short human/machine-readable reason, e.g. "error_rate 27.5 > 20 for 60s"
 * @param windowStart start of the anomalous observation window
 * @param windowEnd   end of the anomalous observation window
 * @param metrics     aggregated metrics over the window (error_rate, error_count, p95_latency_ms, ...)
 * @param deploymentId optional id of the deployment believed to be the cause
 */
public record IncidentCandidate(
        UUID candidateId,
        Instant timestamp,
        String serviceId,
        Env env,
        Severity severity,
        String reason,
        @JsonProperty("windowStart") Instant windowStart,
        @JsonProperty("windowEnd") Instant windowEnd,
        JsonNode metrics,
        @JsonProperty("deploymentId") String deploymentId
) {
    public IncidentCandidate {
        if (candidateId == null) {
            throw new IllegalArgumentException("candidateId must not be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        if (serviceId == null) {
            throw new IllegalArgumentException("serviceId must not be null");
        }
        if (env == null) {
            throw new IllegalArgumentException("env must not be null");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (windowStart == null) {
            throw new IllegalArgumentException("windowStart must not be null");
        }
        if (windowEnd == null) {
            throw new IllegalArgumentException("windowEnd must not be null");
        }
    }
}
