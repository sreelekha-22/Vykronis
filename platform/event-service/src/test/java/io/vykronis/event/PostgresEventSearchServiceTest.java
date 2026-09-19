package io.vykronis.event;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostgresEventSearchServiceTest {

    private final EventRepository repository = mock(EventRepository.class);
    private final PostgresEventSearchService service = new PostgresEventSearchService(repository);

    private final Instant from = Instant.parse("2026-09-03T09:00:00Z");
    private final Instant to = Instant.parse("2026-09-03T11:00:00Z");

    private Event event(String id, Instant ts) {
        Event e = new Event();
        e.setEventId(id);
        e.setSource("payment-service");
        e.setServiceId("payment-service");
        e.setEnv("PROD");
        e.setType("METRIC");
        e.setPayload("{\"error_rate\":25.0}");
        e.setTraceId("tr-1");
        e.setTimestamp(ts);
        e.setStatus("OPEN");
        return e;
    }

    @Test
    void delegatesToWindowedRepositoryQueryAndMapsHits() {
        when(repository.searchByWindow(any(), any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(List.of(
                        event("evt-1", from.plusSeconds(60)),
                        event("evt-2", from.plusSeconds(120))));

        List<EventSearchService.SearchHit> hits =
                service.search(from, to, "payment-service", "METRIC", "tr-1");

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).eventId()).isEqualTo("evt-1");
        assertThat(hits.get(0).serviceId()).isEqualTo("payment-service");
        assertThat(hits.get(0).type()).isEqualTo("METRIC");
        assertThat(hits.get(0).payload()).isEqualTo("{\"error_rate\":25.0}");
        assertThat(hits.get(0).timestamp()).isEqualTo(from.plusSeconds(60));
        assertThat(hits.get(0).status()).isEqualTo("OPEN");
    }

    @Test
    void indexIsNoOp() {
        assertThatCode(() -> service.index(event("evt-1", from))).doesNotThrowAnyException();
    }
}