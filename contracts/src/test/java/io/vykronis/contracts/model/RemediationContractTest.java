package io.vykronis.contracts.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 6 contracts: the incident service issues {@link RemediationCommand}s
 * after policy approval, and remediation-service reports back with
 * {@link RemediationResult}s that drive the incident's verify window.
 */
class RemediationContractTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private RemediationCommand command(String incidentId, RemediationAction action) {
        return new RemediationCommand(
                UUID.randomUUID(), incidentId, action, "payment-service", Env.PROD,
                "1.3.1", Instant.parse("2026-09-07T10:00:00Z"), "ops");
    }

    @Test
    void remediationCommandRoundTripsThroughJackson() throws Exception {
        RemediationCommand command = command("inc-7", RemediationAction.ROLLBACK);

        String json = mapper.writeValueAsString(command);
        RemediationCommand parsed = mapper.readValue(json, RemediationCommand.class);

        assertThat(parsed).isEqualTo(command);
        assertThat(parsed.incidentId()).isEqualTo("inc-7");
        assertThat(parsed.action()).isEqualTo(RemediationAction.ROLLBACK);
        assertThat(parsed.environment()).isEqualTo(Env.PROD);
        assertThat(parsed.targetVersion()).isEqualTo("1.3.1");
        assertThat(parsed.issuedAt().toString()).startsWith("2026-09-07T10:00:00");
    }

    @Test
    void remediationResultRoundTripsThroughJackson() throws Exception {
        UUID commandId = UUID.randomUUID();
        RemediationResult result = new RemediationResult(
                UUID.randomUUID(), commandId, "inc-7", RemediationOutcome.COMPLETED,
                "docker compose up -d payment-service: OK", Instant.parse("2026-09-07T10:05:00Z"));

        String json = mapper.writeValueAsString(result);
        RemediationResult parsed = mapper.readValue(json, RemediationResult.class);

        assertThat(parsed).isEqualTo(result);
        assertThat(parsed.commandId()).isEqualTo(commandId);
        assertThat(parsed.outcome()).isEqualTo(RemediationOutcome.COMPLETED);
    }

    @Test
    void remediationCommandRejectsNullCommandId() {
        assertThatThrownBy(() -> new RemediationCommand(
                null, "inc-7", RemediationAction.ROLLBACK, "payment-service", Env.PROD,
                null, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediationCommandRejectsNullIncidentId() {
        assertThatThrownBy(() -> command(null, RemediationAction.ROLLBACK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediationCommandRejectsNullAction() {
        assertThatThrownBy(() -> command("inc-7", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediationCommandRejectsNullServiceId() {
        assertThatThrownBy(() -> new RemediationCommand(
                UUID.randomUUID(), "inc-7", RemediationAction.ROLLBACK, null, Env.PROD,
                null, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediationCommandRejectsNullEnvironment() {
        assertThatThrownBy(() -> new RemediationCommand(
                UUID.randomUUID(), "inc-7", RemediationAction.RESTART, "payment-service", null,
                null, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediationCommandRejectsNullIssuedAt() {
        assertThatThrownBy(() -> new RemediationCommand(
                UUID.randomUUID(), "inc-7", RemediationAction.ROLLBACK, "payment-service", Env.PROD,
                null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediationResultRejectsNullResultId() {
        assertThatThrownBy(() -> new RemediationResult(
                null, UUID.randomUUID(), "inc-7", RemediationOutcome.FAILED, "boom", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediationResultRejectsNullCommandId() {
        assertThatThrownBy(() -> new RemediationResult(
                UUID.randomUUID(), null, "inc-7", RemediationOutcome.FAILED, "boom", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediationResultRejectsNullOutcome() {
        assertThatThrownBy(() -> new RemediationResult(
                UUID.randomUUID(), UUID.randomUUID(), "inc-7", null, "boom", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remediationResultRejectsNullCompletedAt() {
        assertThatThrownBy(() -> new RemediationResult(
                UUID.randomUUID(), UUID.randomUUID(), "inc-7", RemediationOutcome.COMPLETED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}