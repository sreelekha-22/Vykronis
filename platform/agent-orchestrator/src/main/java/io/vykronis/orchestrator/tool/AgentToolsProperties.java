package io.vykronis.orchestrator.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The allow-listed service endpoints agent tools may reach, declared explicitly
 * and therefore always present and validated: omitting an allow-list or a base
 * URL is a fail-fast startup error, never a silently open hole.
 */
@ConfigurationProperties(prefix = "vykronis.agent.tools")
public class AgentToolsProperties {

    private String incidentUrl;

    private List<String> incidentAllowlist;

    private String eventUrl;

    private List<String> eventAllowlist;

    public String getIncidentUrl() {
        return incidentUrl;
    }

    public void setIncidentUrl(String incidentUrl) {
        this.incidentUrl = incidentUrl;
    }

    public List<String> getIncidentAllowlist() {
        return incidentAllowlist;
    }

    public void setIncidentAllowlist(List<String> incidentAllowlist) {
        this.incidentAllowlist = incidentAllowlist;
    }

    public String getEventUrl() {
        return eventUrl;
    }

    public void setEventUrl(String eventUrl) {
        this.eventUrl = eventUrl;
    }

    public List<String> getEventAllowlist() {
        return eventAllowlist;
    }

    public void setEventAllowlist(List<String> eventAllowlist) {
        this.eventAllowlist = eventAllowlist;
    }
}