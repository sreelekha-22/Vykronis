package io.vykronis.incident;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Outbound client to the agent orchestrator's {@code POST /api/investigations}.
 * Returns the hypothesis JSON document (already schema-validated there) which
 * the incident aggregate persists verbatim. Any non-2xx response is surfaced as
 * {@link InvestigationUnavailableException} so the caller can revert the
 * incident state instead of persisting a half-finished investigation.
 */
@Component
public class InvestigationClient {

    private final RestClient client;

    public InvestigationClient(RestClient.Builder builder,
                               @Value("${vykronis.orchestrator.url:http://localhost:8085}") String orchestratorUrl) {
        this.client = builder.baseUrl(orchestratorUrl).build();
    }

    public String investigate(String incidentId) {
        try {
            return client.post()
                    .uri("/api/investigations")
                    .body(Map.of("incidentId", incidentId))
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new InvestigationUnavailableException(
                    "Agent orchestrator investigation failed for incident " + incidentId, e);
        }
    }
}