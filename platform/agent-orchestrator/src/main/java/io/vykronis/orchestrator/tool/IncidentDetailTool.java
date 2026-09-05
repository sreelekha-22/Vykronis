package io.vykronis.orchestrator.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Allow-listed tool: fetch one incident by id. The id is strictly validated as
 * a UUID so it can never escape the allow-listed path; the client additionally
 * enforces the endpoint/path allow-list before connecting.
 */
@Component
public class IncidentDetailTool implements Tool {

    public static final String ID = "incident.detail";

    private static final ToolSpec SPEC = new ToolSpec(
            ID,
            "Fetches one incident by its incidentId (a UUID) from incident-service. "
                    + "Returns JSON with incidentId, serviceId, env, severity, status, title, "
                    + "description, errorRate, errorCount, windowStart, windowEnd, detectedAt, "
                    + "resolvedAt and metadata.",
            List.of("incidentId"));

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public String invoke(ToolHttpClient http, Map<String, Object> arguments) {
        String incidentId = ToolArguments.requireUuid(arguments, "incidentId").toString();
        return http.get("incident", "/api/incidents/" + incidentId, Map.of());
    }
}