package io.vykronis.common.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vykronis.observability")
public class ObservabilityProperties {

    private String env = "dev";

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }
}