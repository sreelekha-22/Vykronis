package io.vykronis.orchestrator.demo;

import io.vykronis.common.api.ApiError;
import io.vykronis.contracts.model.Env;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Demo endpoint to simulate a deployment (e.g. a faulty Payment deploy). Used
 * by the Phase 2 demo to seed {@code obs.deployments} so incidents get
 * attributed to a version on the timeline.
 */
@RestController
@RequestMapping("/api/demo")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @RequestMapping("/deploy/{serviceId}")
    public ResponseEntity<?> deploy(
            @PathVariable String serviceId,
            @RequestParam(defaultValue = "1.0.0") String version,
            @RequestParam(defaultValue = "PROD") String env) {
        Env parsed;
        try {
            parsed = Env.valueOf(env.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiError.of(400, "BAD_ENV", "Unknown environment: " + env));
        }
        String deploymentId = deploymentService.deploy(serviceId, version, parsed);
        return ResponseEntity.ok(Map.of(
                "deploymentId", deploymentId,
                "serviceId", serviceId,
                "version", version,
                "env", parsed.name()));
    }
}