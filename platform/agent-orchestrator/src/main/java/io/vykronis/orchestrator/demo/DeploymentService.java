package io.vykronis.orchestrator.demo;

import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.DeploymentEvent;
import io.vykronis.contracts.model.DeploymentStatus;
import io.vykronis.contracts.model.Env;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Emits {@link DeploymentEvent} records onto {@code obs.deployments}. The
 * correlation engine joins these against error/latency windows so an incident
 * can be attributed to a specific deployment/version.
 *
 * <p>The producer uses a String serializer, so we marshal the deployment to
 * JSON explicitly with the shared mapper. The correlation side deserializes
 * from the same JSON shape.</p>
 */
@Service
public class DeploymentService {

    public static final String DEPLOYMENTS_TOPIC = "obs.deployments";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public DeploymentService(@org.springframework.beans.factory.annotation.Qualifier("stringKafkaTemplate")
                             KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Records a deployment for a target service.
     *
     * @param serviceId logical service id being deployed
     * @param version   deployed version
     * @param env       target environment
     * @return the emitted deployment id
     */
    public String deploy(String serviceId, String version, Env env) {
        DeploymentEvent event = new DeploymentEvent(
                UUID.randomUUID(),
                serviceId,
                version,
                env,
                Instant.now(),
                Instant.now(),
                DeploymentStatus.SUCCESS,
                "demo-deployer");
        String json;
        try {
            json = Json.mapper().writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize deployment", e);
        }
        kafkaTemplate.send(DEPLOYMENTS_TOPIC, serviceId, json);
        return event.deploymentId().toString();
    }
}