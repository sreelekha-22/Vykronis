package io.vykronis.demo;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.DeploymentEvent;
import io.vykronis.contracts.model.DeploymentStatus;
import io.vykronis.contracts.model.Env;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private DeploymentService service;

    @BeforeEach
    void setUp() {
        service = new DeploymentService(kafkaTemplate);
    }

    @Test
    void deployEmitsSerializableJsonToDeploymentsTopic() throws Exception {
        String deploymentId = service.deploy("payment-service", "2.3.1", Env.PROD);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), payload.capture());

        assertThat(topic.getValue()).isEqualTo(DeploymentService.DEPLOYMENTS_TOPIC);
        assertThat(key.getValue()).isEqualTo("payment-service");

        DeploymentEvent roundTrip = Json.mapper().readValue(payload.getValue(), DeploymentEvent.class);
        assertThat(roundTrip.deploymentId().toString()).isEqualTo(deploymentId);
        assertThat(roundTrip.serviceId()).isEqualTo("payment-service");
        assertThat(roundTrip.version()).isEqualTo("2.3.1");
        assertThat(roundTrip.environment()).isEqualTo(Env.PROD);
        assertThat(roundTrip.status()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(roundTrip.subject()).isEqualTo("demo-deployer");
    }
}
