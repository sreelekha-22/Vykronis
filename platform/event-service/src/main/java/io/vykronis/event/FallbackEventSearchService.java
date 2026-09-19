package io.vykronis.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Primary {@link EventSearchService}: prefers the OpenSearch index and, when it
 * is unreachable, transparently falls back to the Postgres {@code events} table.
 * Indexing still targets OpenSearch (skipped with a warning when it is down) so
 * the richer index is warmed whenever the {@code search} profile joins.
 */
@Service
@Primary
public class FallbackEventSearchService implements EventSearchService {

    private static final Logger log = LoggerFactory.getLogger(FallbackEventSearchService.class);

    private final OpenSearchEventSearchService openSearch;
    private final PostgresEventSearchService postgres;

    public FallbackEventSearchService(OpenSearchEventSearchService openSearch,
                                      PostgresEventSearchService postgres) {
        this.openSearch = openSearch;
        this.postgres = postgres;
    }

    @Override
    public void index(Event event) {
        try {
            openSearch.index(event);
        } catch (RuntimeException e) {
            log.warn("Skipped OpenSearch indexing for {}: {}", event.getEventId(), e.getMessage());
        }
    }

    @Override
    public List<SearchHit> search(Instant from, Instant to, String serviceId, String type, String traceId) {
        try {
            return openSearch.search(from, to, serviceId, type, traceId);
        } catch (RuntimeException e) {
            log.warn("OpenSearch unavailable ({}); falling back to Postgres search", e.getMessage());
            return postgres.search(from, to, serviceId, type, traceId);
        }
    }
}