package io.vykronis.orchestrator.tool;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Wires the only HTTP client agent tools may use, from the allow-listed endpoint
 * configuration. The wired endpoints exist unconditionally: incident-service and
 * event-service — plus anything else a future tool declares in configuration.
 */
@Configuration
@EnableConfigurationProperties(AgentToolsProperties.class)
public class ToolConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    ToolHttpClient toolHttpClient(RestClient.Builder builder, AgentToolsProperties properties) {
        List<ServiceEndpoint> endpoints = new ArrayList<>();
        add(endpoints, "incident", properties.getIncidentUrl(), properties.getIncidentAllowlist());
        add(endpoints, "event", properties.getEventUrl(), properties.getEventAllowlist());
        return new ToolHttpClient(builder, endpoints);
    }

    private void add(List<ServiceEndpoint> endpoints, String name,
                     String baseUrl, List<String> allowlist) {
        endpoints.add(new ServiceEndpoint(name, baseUrl, allowlist == null ? List.of() : allowlist));
    }
}