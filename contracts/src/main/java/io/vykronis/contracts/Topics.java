package io.vykronis.contracts;

/**
 * Canonical Kafka topic names shared across Vykronis services.
 */
public final class Topics {

    public static final String METRICS = "obs.metrics";
    public static final String TRACES = "obs.traces";
    public static final String JFR = "obs.jfr";
    public static final String REMEDIATION = "obs.remediation";

    private Topics() {
    }
}