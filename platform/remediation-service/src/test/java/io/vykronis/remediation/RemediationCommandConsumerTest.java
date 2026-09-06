package io.vykronis.remediation;

import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.RemediationAction;
import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationOutcome;
import io.vykronis.contracts.model.RemediationResult;
import io.vykronis.remediation.executor.RemediationExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RemediationCommandConsumerTest {

    @Test
    void nullCommandIsIgnoredAndNeverProducesAResult() {
        RemediationExecutor executor = mock(RemediationExecutor.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, RemediationResult> template = mock(KafkaTemplate.class);
        RemediationCommandConsumer consumer = new RemediationCommandConsumer(executor, template);

        consumer.onCommand(null);

        verifyNoInteractions(executor);
        verifyNoInteractions(template);
    }

    @Test
    void successfulCommandProducesAResultOnTheSameTopicKeyedByCommandId() {
        UUID commandId = UUID.randomUUID();
        RemediationCommand command = new RemediationCommand(
                commandId, "inc-7", RemediationAction.ROLLBACK, "payment-service", Env.PROD,
                "1.3.1", Instant.parse("2026-09-07T10:00:00Z"), "ops");
        RemediationResult result = new RemediationResult(
                UUID.randomUUID(), commandId, "inc-7",
                RemediationOutcome.COMPLETED, "ok",
                Instant.parse("2026-09-07T10:01:00Z"));

        RemediationExecutor executor = mock(RemediationExecutor.class);
        when(executor.execute(command)).thenReturn(result);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, RemediationResult> template = mock(KafkaTemplate.class);
        RemediationCommandConsumer consumer = new RemediationCommandConsumer(executor, template);

        consumer.onCommand(command);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RemediationResult> value = ArgumentCaptor.forClass(RemediationResult.class);
        verify(template).send(anyString(), key.capture(), value.capture());
        assertThat(key.getValue()).isEqualTo(commandId.toString());
        assertThat(value.getValue()).isEqualTo(result);
    }
}
