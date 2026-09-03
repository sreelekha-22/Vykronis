package io.vykronis.contracts.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * A deployment record emitted by the demo/CI pipeline onto the
 * {@code obs.deployments} topic. The correlation engine joins these against
 * error/latency windows to attribute an anomaly to a particular deployment.
 *
 * <p>Key on {@code serviceId} (same partition key as {@link ObservabilityEvent}
 * metrics) so the join is co-partitioned.</p>
 *
 * @param deploymentId unique deployment id
 * @param serviceId    logical service id that was deployed (partition key)
 * @param version      deployed version, e.g. "1.4.2"
 * @param environment  target environment (DEV / PROD)
 * @param startedAt    deploy start instant
 * @param finishedAt   deploy completion instant (null if still deploying)
 * @param status       DEPLOYING / SUCCESS / FAILED / ROLLED_BACK
 * @param subject      identity that triggered the deployment
 */
public record DeploymentEvent(
        @JsonProperty("deploymentId") UUID deploymentId,
        @JsonProperty("serviceId") String serviceId,
        String version,
        Env environment,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("finishedAt") Instant finishedAt,
        DeploymentStatus status,
        String subject
) {
    public DeploymentEvent {
        if (deploymentId == null) {
            throw new IllegalArgumentException("deploymentId must not be null");
        }
        if (serviceId == null) {
            throw new IllegalArgumentException("serviceId must not be null");
        }
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
