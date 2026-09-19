package io.vykronis.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackEventSearchServiceTest {

    private final OpenSearchEventSearchService openSearch = mock(OpenSearchEventSearchService.class);
    private final PostgresEventSearchService postgres = mock(PostgresEventSearchService.class);
    private final FallbackEventSearchService service = new FallbackEventSearchService(openSearch, postgres);

    private final Instant from = Instant.parse("2026-09-03T09:00:00Z");
    private final Instant to = Instant.parse("2026-09-03T11:00:00Z");
    private final EventSearchService.SearchHit hit = new EventSearchService.SearchHit(
            "evt-1", "payment-service", "payment-service", "PROD", "METRIC",
            "{\"error_rate\":25.0}", "tr-1", from.plusSeconds(60), "OPEN");

    @Test
    void searchPrefersOpenSearchWhenAvailable() {
        when(openSearch.search(from, to, "payment-service", "METRIC", "tr-1"))
                .thenReturn(List.of(hit));

        List<EventSearchService.SearchHit> result =
                service.search(from, to, "payment-service", "METRIC", "tr-1");

        assertThat(result).containsExactly(hit);
        verify(postgres, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void searchFallsBackToPostgresWhenOpenSearchFails() {
        when(openSearch.search(from, to, "payment-service", null, null))
                .thenThrow(new IllegalStateException("connection refused"));
        when(postgres.search(from, to, "payment-service", null, null))
                .thenReturn(List.of(hit));

        List<EventSearchService.SearchHit> result =
                service.search(from, to, "payment-service", null, null);

        assertThat(result).containsExactly(hit);
    }

    @Test
    void indexDelegatesToOpenSearch() {
        service.index(sampleEvent());

        verify(openSearch).index(any(Event.class));
    }

    @Test
    void indexSwallowsOpenSearchFailure() {
        doThrow(new IllegalStateException("connection refused")).when(openSearch).index(any(Event.class));

        assertThatCode(() -> service.index(sampleEvent())).doesNotThrowAnyException();
    }

    private static Event sampleEvent() {
        Event e = new Event();
        e.setEventId("evt-1");
        e.setServiceId("payment-service");
        e.setEnv("PROD");
        e.setType("METRIC");
        e.setTimestamp(Instant.parse("2026-09-03T10:00:00Z"));
        return e;
    }
}