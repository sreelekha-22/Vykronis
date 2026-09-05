package io.vykronis.event;

import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.ObservabilityEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code obs.metrics} ({@link ObservabilityEvent} emitted by the
 * ingest/demo pipeline) and persists each event to Postgres, then indexes it in
 * OpenSearch so agents can search evidence by incident window.
 *
 * <p>Idempotency: an event id already persisted is skipped entirely (no
 * duplicate rows or documents), matching the repository's unique
 * {@code event_id} constraint.</p>
 */
@Service
public class ObservabilityEventConsumer {

    private final EventRepository repository;
    private final EventSearchService searchService;

    public ObservabilityEventConsumer(EventRepository repository, EventSearchService searchService) {
        this.repository = repository;
        this.searchService = searchService;
    }

    @KafkaListener(topics = Topics.METRICS, groupId = "event-service",
            containerFactory = "eventKafkaListenerContainerFactory")
    @Transactional
    public void onEvent(ObservabilityEvent event) {
        if (event == null) {
            return;
        }
        if (repository.existsByEventId(event.id().toString())) {
            return;
        }
        Event entity = new Event(
                event.id().toString(),
                event.source(),
                event.serviceId(),
                event.env().name(),
                event.type().name(),
                event.payload() != null ? event.payload().toString() : null,
                event.traceId(),
                event.timestamp());
        repository.save(entity);
        searchService.index(entity);
    }
}