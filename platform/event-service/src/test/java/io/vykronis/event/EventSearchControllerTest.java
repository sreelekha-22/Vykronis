package io.vykronis.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventSearchControllerTest {

    private final EventSearchService searchService = mock(EventSearchService.class);
    private final EventSearchController controller = new EventSearchController(searchService);

    private final Instant from = Instant.parse("2026-09-03T10:00:00Z");
    private final Instant to = Instant.parse("2026-09-03T11:00:00Z");

    @Test
    void delegatesToSearchServiceWithParsedWindowAndFilters() {
        EventSearchService.SearchHit hit = new EventSearchService.SearchHit(
                "evt-1", "payment-service", "payment-service", "PROD", "LOG",
                "{\"value\":1}", "trace-a", from.plusSeconds(60), "OPEN");
        when(searchService.search(from, to, "payment-service", "LOG", "trace-a"))
                .thenReturn(List.of(hit));

        List<EventSearchService.SearchHit> result =
                controller.search(from.toString(), to.toString(), "payment-service", "LOG", "trace-a");

        assertThat(result).containsExactly(hit);
        verify(searchService).search(from, to, "payment-service", "LOG", "trace-a");
    }

    @Test
    void delegatesWithoutOptionalFiltersWhenAbsent() {
        controller.search(from.toString(), to.toString(), null, null, null);

        verify(searchService).search(from, to, null, null, null);
    }

    @Test
    void rejectsMalformedWindowParameter() {
        assertThatThrownBy(() -> controller.search("not-a-date", to.toString(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    void rejectsWindowWhereFromIsAfterTo() {
        assertThatThrownBy(() -> controller.search(to.toString(), from.toString(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from must not be later than to");
    }
}