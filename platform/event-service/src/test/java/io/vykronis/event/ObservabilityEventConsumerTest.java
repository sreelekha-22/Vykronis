package io.vykronis.event;

import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.EventType;
import io.vykronis.contracts.model.ObservabilityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityEventConsumerTest {

    private final EventRepository repository = mock(EventRepository.class);
    private final EventSearchService searchService = mock(EventSearchService.class);
    private final ObservabilityEventConsumer consumer = new ObservabilityEventConsumer(repository, searchService);

    private ObservabilityEvent event;

    @BeforeEach
    void setUp() throws Exception {
        event = new ObservabilityEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Instant.parse("2026-09-03T10:00:00Z"),
                "order-service",
                "order-service",
                Env.PROD,
                EventType.METRIC,
                io.vykronis.common.json.Json.mapper().readTree("{\"value\":1}"),
                "trace-1");
    }

    @Test
    void persistsAndIndexesNewEvent() {
        when(repository.existsByEventId(event.id().toString())).thenReturn(false);

        consumer.onEvent(event);

        org.mockito.ArgumentCaptor<Event> captor = org.mockito.ArgumentCaptor.forClass(Event.class);
        verify(repository).save(captor.capture());
        Event saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(event.id().toString());
        assertThat(saved.getSource()).isEqualTo("order-service");
        assertThat(saved.getServiceId()).isEqualTo("order-service");
        assertThat(saved.getEnv()).isEqualTo("PROD");
        assertThat(saved.getType()).isEqualTo("METRIC");
        assertThat(saved.getTimestamp()).isEqualTo(Instant.parse("2026-09-03T10:00:00Z"));
        assertThat(saved.getTraceId()).isEqualTo("trace-1");
        verify(searchService).index(saved);
    }

    @Test
    void skipsAlreadyPersistedEvent() {
        when(repository.existsByEventId(event.id().toString())).thenReturn(true);

        consumer.onEvent(event);

        verify(repository, never()).save(any(Event.class));
        verify(searchService, never()).index(any(Event.class));
    }

    @Test
    void ignoresNullMessage() {
        consumer.onEvent(null);

        verify(repository, never()).save(any(Event.class));
        verify(searchService, never()).index(any(Event.class));
    }
}