package io.vykronis.orchestrator.tool;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allow-listed tool: search observability evidence (logs/traces/metrics)
 * indexed by event-service within a time window. from/to are required
 * ISO-8601 instants; serviceId, type and traceId are optional filters.
 */
@Component
public class EvidenceSearchTool implements Tool {

    public static final String ID = "evidence.search";

    private static final ToolSpec SPEC = new ToolSpec(
            ID,
            "Searches observability evidence (LOG/TRACE/METRIC events) indexed by event-service "
                    + "within the [from, to] time window. from and to are required ISO-8601 instants; "
                    + "serviceId, type and traceId are optional filters. Returns a JSON array of "
                    + "search hits with eventId, source, serviceId, env, type, payload, traceId, "
                    + "timestamp and status.",
            List.of("from", "to", "serviceId?", "type?", "traceId?"));

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public String invoke(ToolHttpClient http, Map<String, Object> arguments) {
        Instant from = ToolArguments.requireInstant(arguments, "from");
        Instant to = ToolArguments.requireInstant(arguments, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be later than to");
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("from", from.toString());
        query.put("to", to.toString());
        String serviceId = ToolArguments.optional(arguments, "serviceId");
        String type = ToolArguments.optional(arguments, "type");
        String traceId = ToolArguments.optional(arguments, "traceId");
        if (serviceId != null) {
            query.put("serviceId", serviceId);
        }
        if (type != null) {
            query.put("type", type);
        }
        if (traceId != null) {
            query.put("traceId", traceId);
        }
        return http.get("event", "/api/search/events", query);
    }
}