package io.vykronis.orchestrator.demo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DeploymentServiceTest {

    @Test
    void deploySerialisesAValidDeploymentEventOnTheDeploymentsTopic() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        DeploymentService service = new DeploymentService(template);

        String id = service.deploy("payment-service", "1.3.1", io.vykronis.contracts.model.Env.PROD);

        assertThat(id).isNotBlank();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(template).send(org.mockito.ArgumentMatchers.eq(DeploymentService.DEPLOYMENTS_TOPIC),
                org.mockito.ArgumentMatchers.eq("payment-service"), body.capture());
        assertThat(body.getValue()).contains("\"serviceId\":\"payment-service\"");
        assertThat(body.getValue()).contains("\"version\":\"1.3.1\"");
        assertThat(body.getValue()).contains("\"environment\":\"PROD\"");
    }

    @Test
    void noTopicSendWhenDeployIsNotCalled() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        new DeploymentService(template);

        verifyNoInteractions(template);
    }
}