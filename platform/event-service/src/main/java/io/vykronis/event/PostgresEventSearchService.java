package io.vykronis.event;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * {@link EventSearchService} backed by the Postgres {@code events} table, which
 * is the platform's system of record for ingested telemetry. Used as the
 * fallback for {@link FallbackEventSearchService} whenever the optional
 * OpenSearch index is unavailable, so agents and the incident UI always get
 * windowed evidence even when the {@code search} profile is not running.
 */
@Service
public class PostgresEventSearchService implements EventSearchService {

    private static final int MAX_HITS = 500;

    private final EventRepository repository;

    public PostgresEventSearchService(EventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void index(Event event) {
        // Postgres is already the system of record; nothing to index.
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> search(Instant from, Instant to, String serviceId, String type, String traceId) {
        return repository.searchByWindow(from, to, serviceId, type, traceId, PageRequest.of(0, MAX_HITS))
                .stream()
                .map(this::toHit)
                .toList();
    }

    private SearchHit toHit(Event event) {
        return new SearchHit(
                event.getEventId(),
                event.getSource(),
                event.getServiceId(),
                event.getEnv(),
                event.getType(),
                event.getPayload(),
                event.getTraceId(),
                event.getTimestamp(),
                event.getStatus());
    }
}