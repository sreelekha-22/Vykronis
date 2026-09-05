package io.vykronis.orchestrator.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The allow-listed tool catalog. Names are stable identifiers the agent (and
 * later the provider/fallback decision-making) may invoke; invoking anything
 * not registered here fails with {@link UnknownToolException} before any effect
 * can happen.
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            register(tool);
        }
    }

    public void register(Tool tool) {
        if (tool.spec() == null || tool.spec().id() == null || tool.spec().id().isBlank()) {
            throw new IllegalArgumentException("Tool must expose a non-blank id");
        }
        tools.put(tool.spec().id(), tool);
    }

    /** Catalogue offered to decision-making: allow-listed tools only, stable order. */
    public List<ToolSpec> describeAll() {
        return tools.values().stream()
                .sorted(java.util.Comparator.comparing(t -> t.spec().id()))
                .map(Tool::spec)
                .toList();
    }

    /**
     * Invokes {@code toolId}. Unknown ids raise {@link UnknownToolException}
     * without contacting any service.
     */
    public String invoke(String toolId, ToolHttpClient http, Map<String, Object> arguments) {
        Tool tool = tools.get(toolId);
        if (tool == null) {
            throw new UnknownToolException(toolId);
        }
        return tool.invoke(http, arguments);
    }
}