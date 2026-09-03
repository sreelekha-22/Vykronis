package io.vykronis.contracts.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractNullValidationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ObjectNode metrics() {
        return mapper.createObjectNode().put("error_rate", 30.0);
    }

    @Test
    void incidentCandidateRejectsNullCandidateId() {
        assertThatThrownBy(() -> candidateWithId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incidentCandidateRejectsNullServiceId() {
        assertThatThrownBy(() -> candidateWithService(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incidentCandidateRejectsNullEnv() {
        assertThatThrownBy(() -> new IncidentCandidate(
                UUID.randomUUID(), Instant.now(), "s", null, Severity.HIGH, "r",
                Instant.now(), Instant.now(), metrics(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incidentCandidateRejectsNullSeverity() {
        assertThatThrownBy(() -> new IncidentCandidate(
                UUID.randomUUID(), Instant.now(), "s", Env.PROD, null, "r",
                Instant.now(), Instant.now(), metrics(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incidentCandidateRejectsNullWindowStart() {
        assertThatThrownBy(() -> new IncidentCandidate(
                UUID.randomUUID(), Instant.now(), "s", Env.PROD, Severity.HIGH, "r",
                null, Instant.now(), metrics(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incidentCandidateRejectsNullWindowEnd() {
        assertThatThrownBy(() -> new IncidentCandidate(
                UUID.randomUUID(), Instant.now(), "s", Env.PROD, Severity.HIGH, "r",
                Instant.now(), null, metrics(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deploymentRejectsNullDeploymentId() {
        assertThatThrownBy(() -> new DeploymentEvent(
                null, "s", "1.0", Env.PROD,
                Instant.now(), Instant.now(), DeploymentStatus.SUCCESS, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deploymentRejectsNullServiceId() {
        assertThatThrownBy(() -> new DeploymentEvent(
                UUID.randomUUID(), null, "1.0", Env.PROD,
                Instant.now(), Instant.now(), DeploymentStatus.SUCCESS, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deploymentRejectsNullVersion() {
        assertThatThrownBy(() -> new DeploymentEvent(
                UUID.randomUUID(), "s", null, Env.PROD,
                Instant.now(), Instant.now(), DeploymentStatus.SUCCESS, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deploymentRejectsNullEnv() {
        assertThatThrownBy(() -> new DeploymentEvent(
                UUID.randomUUID(), "s", "1.0", null,
                Instant.now(), Instant.now(), DeploymentStatus.SUCCESS, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deploymentRejectsNullStatus() {
        assertThatThrownBy(() -> new DeploymentEvent(
                UUID.randomUUID(), "s", "1.0", Env.PROD,
                Instant.now(), Instant.now(), null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void observabilityEventRejectsNullId() {
        assertThatThrownBy(() -> new ObservabilityEvent(
                null, Instant.now(), "src", "s", Env.PROD, EventType.METRIC, metrics(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void observabilityEventRejectsNullType() {
        assertThatThrownBy(() -> new ObservabilityEvent(
                UUID.randomUUID(), Instant.now(), "src", "s", Env.PROD, null, metrics(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private IncidentCandidate candidateWithId(UUID id) {
        return new IncidentCandidate(id, Instant.now(), "s", Env.PROD, Severity.HIGH, "r",
                Instant.now(), Instant.now(), metrics(), null);
    }

    private IncidentCandidate candidateWithService(String service) {
        return new IncidentCandidate(UUID.randomUUID(), Instant.now(), service, Env.PROD, Severity.HIGH, "r",
                Instant.now(), Instant.now(), metrics(), null);
    }
}
