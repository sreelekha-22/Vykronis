package io.vykronis.event;

import java.time.Instant;
import java.util.List;

/**
 * Search surface over indexed observability evidence (the "search APIs for
 * agents" feature). Agents query logs/traces/metrics within an incident's
 * [from, to] time window to assemble a timeline of evidence.
 */
public interface EventSearchService {

    /**
     * Indexes an {@link Event} so it becomes searchable. Idempotent per event
     * id (re-indexing overwrites the document).
     *
     * @param event the persisted event to index
     */
    void index(Event event);

    /**
     * Searches indexed events inside a closed time window, optionally narrowed
     * by service, type, and/or trace id.
     *
     * @param from      window start (inclusive)
     * @param to        window end (inclusive)
     * @param serviceId optional service id filter (blank = all)
     * @param type      optional event type filter (blank = all)
     * @param traceId   optional trace id filter (blank = all)
     * @return matching hits ordered by timestamp ascending
     */
    List<SearchHit> search(Instant from, Instant to, String serviceId, String type, String traceId);

    /**
     * A single indexed evidence document surfaced to an agent.
     */
    record SearchHit(
            String eventId,
            String source,
            String serviceId,
            String env,
            String type,
            String payload,
            String traceId,
            Instant timestamp,
            String status) {
    }
}