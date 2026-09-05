package io.vykronis.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Search integration test against a real OpenSearch via Testcontainers.
 * Verifies that a persisted {@link Event} is indexed and searchable within its
 * incident time window (the "search APIs for agents by incident window"
 * feature).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class EventSearchIntegrationTest {

    @Container
    static final OpenSearchContainer<?> OPENSEARCH = new OpenSearchContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.15.0"));

    @DynamicPropertySource
    static void openSearch(DynamicPropertyRegistry registry) {
        registry.add("vykronis.search.url",
                () -> "http://" + OPENSEARCH.getHost() + ":" + OPENSEARCH.getMappedPort(9200));
    }

    @Autowired
    private EventSearchService searchService;

    @Autowired
    private RestClient opensearchRestClient;

    @BeforeEach
    void resetIndex() throws java.io.IOException {
        Request reset = new Request("POST", "/obs-events/_delete_by_query");
        reset.setJsonEntity("{\"query\":{\"match_all\":{}}}");
        opensearchRestClient.performRequest(reset);
    }

    private Event sampleEvent(String id, String serviceId, String type, String traceId, Instant ts) {
        return new Event(id, serviceId, serviceId, "PROD", type, "{\"value\":1}", traceId, ts);
    }

    @Test
    void searchReturnsOnlyEventsInsideIncidentWindow() {
        Instant from = Instant.parse("2026-09-03T10:00:00Z");
        Instant to = Instant.parse("2026-09-03T11:00:00Z");
        searchService.index(sampleEvent("evt-in-1", "payment-service", "LOG", "trace-a", from.plusSeconds(60)));
        searchService.index(sampleEvent("evt-in-2", "payment-service", "METRIC", "trace-a", to.minusSeconds(60)));
        searchService.index(sampleEvent("evt-out", "payment-service", "LOG", "trace-b", to.plusSeconds(60)));

        List<EventSearchService.SearchHit> hits = searchService.search(from, to, null, null, null);

        assertThat(hits).extracting(EventSearchService.SearchHit::eventId)
                .containsExactlyInAnyOrder("evt-in-1", "evt-in-2");
    }

    @Test
    void searchFiltersByServiceAndType() {
        Instant from = Instant.parse("2026-09-03T10:00:00Z");
        Instant to = Instant.parse("2026-09-03T11:00:00Z");
        searchService.index(sampleEvent("evt-log", "payment-service", "LOG", "trace-a", from.plusSeconds(60)));
        searchService.index(sampleEvent("evt-metric", "payment-service", "METRIC", "trace-a", from.plusSeconds(120)));
        searchService.index(sampleEvent("evt-other-svc", "order-service", "LOG", "trace-b", from.plusSeconds(180)));

        List<EventSearchService.SearchHit> hits =
                searchService.search(from, to, "payment-service", "LOG", null);

        assertThat(hits).extracting(EventSearchService.SearchHit::eventId)
                .containsExactly("evt-log");
    }

    @Test
    void searchByTraceIdFindsEvidenceForAnAgent() {
        Instant from = Instant.parse("2026-09-03T10:00:00Z");
        Instant to = Instant.parse("2026-09-03T11:00:00Z");
        searchService.index(sampleEvent("evt-t", "payment-service", "METRIC", "trace-tgt", from.plusSeconds(60)));
        searchService.index(sampleEvent("evt-other", "payment-service", "METRIC", "trace-other", from.plusSeconds(90)));

        List<EventSearchService.SearchHit> hits =
                searchService.search(from, to, null, null, "trace-tgt");

        assertThat(hits).extracting(EventSearchService.SearchHit::eventId)
                .containsExactly("evt-t");
        assertThat(hits.get(0).timestamp()).isEqualTo(from.plusSeconds(60));
        assertThat(hits.get(0).serviceId()).isEqualTo("payment-service");
        assertThat(hits.get(0).traceId()).isEqualTo("trace-tgt");
    }

    @Test
    void emptyResultsWhenNoEventInWindow() {
        Instant from = Instant.parse("2026-09-03T10:00:00Z");
        Instant to = Instant.parse("2026-09-03T11:00:00Z");
        searchService.index(sampleEvent("evt-late", "payment-service", "LOG", "trace-a", to.plusSeconds(600)));

        assertThat(searchService.search(from, to, null, null, null)).isEmpty();
    }
}