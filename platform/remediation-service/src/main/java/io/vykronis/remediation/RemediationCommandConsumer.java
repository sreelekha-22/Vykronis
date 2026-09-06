package io.vykronis.remediation;

import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationResult;
import io.vykronis.remediation.executor.RemediationExecutor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Executes policy-approved {@link RemediationCommand}s on {@code obs.remediation}
 * and publishes the {@link RemediationResult} back on the same topic for the
 * incident service to advance the incident into VERIFYING.
 *
 * <p>Commands only arrive after the policy service granted the action, and the
 * executor never runs the same {@code commandId} twice (idempotency key).</p>
 */
@Service
public class RemediationCommandConsumer {

    private final RemediationExecutor executor;
    private final KafkaTemplate<String, RemediationResult> kafkaTemplate;

    public RemediationCommandConsumer(RemediationExecutor executor,
                                      KafkaTemplate<String, RemediationResult> kafkaTemplate) {
        this.executor = executor;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = Topics.REMEDIATION, groupId = "remediation-service")
    public void onCommand(RemediationCommand command) {
        if (command == null) {
            return;
        }
        RemediationResult result = executor.execute(command);
        kafkaTemplate.send(Topics.REMEDIATION, command.commandId().toString(), result);
    }
}