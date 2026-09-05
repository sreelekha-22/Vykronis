package io.vykronis.orchestrator;

import io.vykronis.orchestrator.agent.InvestigationAgent;
import io.vykronis.orchestrator.ai.AiGateway;
import io.vykronis.orchestrator.tool.ToolHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test proving the Phase 4 wiring is real: the default application boots
 * with AI unavailable (provider none), the allow-listed tool catalogue is
 * populated from properties and the enforced HTTP client is present.
 */
@SpringBootTest
class AgentOrchestratorApplicationTests {

    @Autowired
    private InvestigationAgent agent;

    @Autowired
    private AiGateway gateway;

    @Autowired
    private ToolHttpClient toolHttpClient;

    @Test
    void contextBootsWithDenyByDefaultSecurityAndRealTools() {
        assertThat(agent).isNotNull();
        assertThat(gateway.isAvailable()).isFalse();
        assertThat(toolHttpClient).isNotNull();

        List<String> toolIds = agent.availableTools().stream()
                .map(t -> t.id())
                .collect(Collectors.toList());
        assertThat(toolIds).containsExactly("evidence.search", "incident.detail", "incident.list");
    }
}