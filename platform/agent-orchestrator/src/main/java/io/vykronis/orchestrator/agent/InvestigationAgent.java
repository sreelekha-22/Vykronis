package io.vykronis.orchestrator.agent;

import io.vykronis.orchestrator.tool.ToolHttpClient;
import io.vykronis.orchestrator.tool.ToolRegistry;
import io.vykronis.orchestrator.tool.ToolSpec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The investigation agent. Its effect surface is exactly the allow-listed tool
 * catalog: {@link #availableTools()} is what any decision-maker (LLM provider in
 * Phase 4 Unit 4+, or the rule-based fallback in Unit 5) may choose from, and
 * {@link #invoke} is the ONLY way an effect happens — always through
 * {@link ToolHttpClient}, so even a misbehaving tool cannot reach a
 * non-allow-listed endpoint. There is no Docker/Kubernetes/admin tool by design.
 */
@Service
public class InvestigationAgent {

    private final ToolRegistry registry;
    private final ToolHttpClient http;

    public InvestigationAgent(ToolRegistry registry, ToolHttpClient http) {
        this.registry = registry;
        this.http = http;
    }

    public List<ToolSpec> availableTools() {
        return registry.describeAll();
    }

    public String invoke(String toolId, Map<String, Object> arguments) {
        return registry.invoke(toolId, http, arguments);
    }
}