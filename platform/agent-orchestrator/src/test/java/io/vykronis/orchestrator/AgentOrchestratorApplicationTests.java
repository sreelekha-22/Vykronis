package io.vykronis.orchestrator;

import io.vykronis.orchestrator.agent.InvestigationAgent;
import io.vykronis.orchestrator.ai.AiGateway;
import io.vykronis.orchestrator.hypothesis.Hypothesis;
import io.vykronis.orchestrator.hypothesis.HypothesisSource;
import io.vykronis.orchestrator.hypothesis.HypothesisValidationException;
import io.vykronis.orchestrator.hypothesis.HypothesisValidator;
import io.vykronis.orchestrator.tool.ToolHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Smoke test proving the Phase 4 wiring is real: the default application boots
 * with AI unavailable (provider none), the allow-listed tool catalogue is
 * populated from properties, the enforced HTTP client is present and the
 * hypothesis JSON Schema validator is wired — schema-conform model output maps
 * to a {@link Hypothesis}, anything else is rejected (Phase 4 Unit 3).
 */
@SpringBootTest
class AgentOrchestratorApplicationTests {

    @Autowired
    private InvestigationAgent agent;

    @Autowired
    private AiGateway gateway;

    @Autowired
    private ToolHttpClient toolHttpClient;

    @Autowired
    private HypothesisValidator hypothesisValidator;

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

    @Test
    void hypothesisValidatorIsWiredAndRejectsRawOutputWithoutStatement() {
        assertThat(hypothesisValidator).isNotNull();

        Hypothesis accepted = hypothesisValidator.validate("""
                {
                  "incidentId": "%s",
                  "statement": "payment-service degraded after v1.4.2 rollout",
                  "confidence": 0.82,
                  "affectedServiceId": "payment-service",
                  "affectedServiceVersion": "v1.4.2",
                  "source": "ai",
                  "evidence": [
                    { "eventId": "ev-1", "type": "TRACE", "source": "payment-service" }
                  ]
                }
                """.formatted(UUID.randomUUID()));
        assertThat(accepted.source()).isEqualTo(HypothesisSource.AI);

        assertThrows(HypothesisValidationException.class, () -> hypothesisValidator.validate("""
                        {
                          "incidentId": "%s",
                          "confidence": 0.5,
                          "affectedServiceId": "payment-service",
                          "source": "ai",
                          "evidence": [ { "eventId": "ev-1", "type": "LOG", "source": "payment-service" } ]
                        }
                        """.formatted(UUID.randomUUID())));
    }
}