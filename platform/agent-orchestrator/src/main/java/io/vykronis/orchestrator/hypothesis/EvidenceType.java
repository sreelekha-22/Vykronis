package io.vykronis.orchestrator.hypothesis;

/**
 * Observability evidence type referenced by a hypothesis, aligned with the
 * evidence timeline refs surfaced to the incident UI.
 */
public enum EvidenceType {

    LOG,
    TRACE,
    METRIC,
    JFR
}