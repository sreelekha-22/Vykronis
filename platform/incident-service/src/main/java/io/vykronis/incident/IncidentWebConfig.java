package io.vykronis.incident;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Boot 4 does not auto-register a {@code RestClient.Builder} bean for plain
 * servlet apps (agent-orchestrator defines its own in ToolConfig); provide one
 * so the {@link InvestigationClient} and {@link PolicyClient} can be wired.
 */
@Configuration
public class IncidentWebConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}