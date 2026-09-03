package io.vykronis.contracts.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationContractsTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void incidentCandidateRoundTrips() throws Exception {
        ObjectNode metrics = mapper.createObjectNode();
        metrics.put("error_rate", 27.5d);
        metrics.put("error_count", 88);
        metrics.put("p95_latency_ms", 1200d);

        IncidentCandidate candidate = new IncidentCandidate(
                UUID.randomUUID(),
                Instant.parse("2026-08-29T10:00:00Z"),
                "payment-service",
                Env.PROD,
                Severity.HIGH,
                "error_rate 27.5 > 20 for 60s",
                Instant.parse("2026-08-29T09:59:00Z"),
                Instant.parse("2026-08-29T10:00:00Z"),
                metrics,
                UUID.randomUUID().toString());

        String json = mapper.writeValueAsString(candidate);
        IncidentCandidate parsed = mapper.readValue(json, IncidentCandidate.class);

        assertThat(parsed).isEqualTo(candidate);
        assertThat(parsed.severity()).isEqualTo(Severity.HIGH);
        assertThat(parsed.metrics().get("error_rate").asDouble()).isEqualTo(27.5d);
        assertThat(parsed.deploymentId()).isNotBlank();
    }

    @Test
    void deploymentEventRoundTrips() throws Exception {
        DeploymentEvent deploy = new DeploymentEvent(
                UUID.randomUUID(),
                "payment-service",
                "1.4.2",
                Env.PROD,
                Instant.parse("2026-08-29T09:58:00Z"),
                Instant.parse("2026-08-29T09:59:30Z"),
                DeploymentStatus.SUCCESS,
                "ci-agent");

        String json = mapper.writeValueAsString(deploy);
        DeploymentEvent parsed = mapper.readValue(json, DeploymentEvent.class);

        assertThat(parsed).isEqualTo(deploy);
        assertThat(parsed.version()).isEqualTo("1.4.2");
        assertThat(parsed.status()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(parsed.environment()).isEqualTo(Env.PROD);
    }
}
