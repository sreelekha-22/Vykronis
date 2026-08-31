package io.vykronis.contracts.model;

/**
 * Canonical observability event type carried by an {@link ObservabilityEvent}.
 */
public enum EventType {
    METRIC,
    LOG,
    TRACE,
    JFR,
    DEPLOY
}
