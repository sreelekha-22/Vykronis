package io.vykronis.remediation.store;

import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.RemediationAction;
import io.vykronis.contracts.model.RemediationOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RemediationRecordEntityTest {

    @Test
    void entityRoundTripsToRecord() {
        UUID commandId = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-09-07T10:00:00Z");
        RemediationRecord record = new RemediationRecord(
                commandId,
                "inc-7",
                RemediationAction.ROLLBACK,
                "payment-service",
                Env.PROD,
                "1.3.1",
                "ops",
                RemediationOutcome.COMPLETED,
                "done",
                completedAt);

        RemediationRecordEntity entity = new RemediationRecordEntity(record);

        assertThat(entity.toRecord()).isEqualTo(record);
    }
}
