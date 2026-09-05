package io.vykronis.orchestrator.hypothesis;

/**
 * A single piece of evidence a hypothesis rests on, mirroring the event-service
 * search-hit contract used by the incident UI timeline.
 *
 * @param eventId  observable event identifier
 * @param type      LOG / TRACE / METRIC / JFR
 * @param source    service / log source
 * @param summary   optional short quote or what the evidence shows
 */
public record HypothesisEvidence(String eventId, EvidenceType type, String source, String summary) {
}