package io.vykronis.orchestrator.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allow-listed tool: list incidents, newest first, with an optional status
 * filter (e.g. INVESTIGATING). Only the incident endpoint allow-list applies —
 * there is no admin surface reachable from here.
 */
@Component
public class IncidentListTool implements Tool {

    public static final String ID = "incident.list";

    private static final ToolSpec SPEC = new ToolSpec(
            ID,
            "Lists incidents, newest first, from incident-service. status is optional "
                    + "(e.g. INVESTIGATING, HYPOTHESIS_READY). Returns a JSON array of incident summaries.",
            List.of("status?"));

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public String invoke(ToolHttpClient http, Map<String, Object> arguments) {
        String status = ToolArguments.optional(arguments, "status");
        Map<String, String> query = new LinkedHashMap<>();
        if (status != null) {
            query.put("status", status);
        }
        return http.get("incident", "/api/incidents", query);
    }
}