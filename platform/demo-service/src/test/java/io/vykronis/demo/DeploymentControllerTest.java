package io.vykronis.demo;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentControllerTest {

    @Test
    void deployReturnsDeploymentIdAndEchoesInputsOnTheHappyPath() {
        DeploymentService service = new DeploymentService(org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class));
        DeploymentController controller = new DeploymentController(service);

        @SuppressWarnings("unchecked")
        org.springframework.http.ResponseEntity<Map<String, Object>> response =
                (org.springframework.http.ResponseEntity<Map<String, Object>>) (org.springframework.http.ResponseEntity<?>)
                controller.deploy("payment-service", "1.3.1", "PROD");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("serviceId", "payment-service");
        assertThat(body).containsEntry("version", "1.3.1");
        assertThat(body).containsEntry("env", "PROD");
        assertThat(body.get("deploymentId")).isNotNull();
    }

    @Test
    void unknownEnvironmentReturns400() {
        DeploymentService service = new DeploymentService(org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class));
        DeploymentController controller = new DeploymentController(service);

        @SuppressWarnings("unchecked")
        org.springframework.http.ResponseEntity<?> response = controller.deploy("payment-service", "1.3.1", "MARS");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
