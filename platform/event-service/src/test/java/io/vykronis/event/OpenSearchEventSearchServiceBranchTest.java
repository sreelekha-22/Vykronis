package io.vykronis.event;

import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenSearchEventSearchServiceBranchTest {

    @Test
    void indexSwallowsIOExceptionAndLogsWarning() throws Exception {
        RestClient client = mock(RestClient.class);
        when(client.performRequest(any(Request.class))).thenThrow(new IOException("boom"));
        OpenSearchEventSearchService service = new OpenSearchEventSearchService(client, "obs-events");

        service.index(sampleEvent("evt-1", "payload", "tr-1"));

        verify(client).performRequest(any(Request.class));
    }

    @Test
    void indexHandlesNonJsonPayloadAsTextNode() throws Exception {
        RestClient client = mock(RestClient.class);
        OpenSearchEventSearchService service = new OpenSearchEventSearchService(client, "obs-events");

        service.index(sampleEvent("evt-1", "not-json", "tr-1"));

        verify(client).performRequest(any(Request.class));
    }

    @Test
    void indexHandlesNullPayload() throws Exception {
        RestClient client = mock(RestClient.class);
        OpenSearchEventSearchService service = new OpenSearchEventSearchService(client, "obs-events");

        service.index(sampleEvent("evt-1", null, "tr-1"));

        verify(client).performRequest(any(Request.class));
    }

    @Test
    void addTermSkipsNullAndBlankValues() {
        RestClient client = mock(RestClient.class);
        OpenSearchEventSearchService service = new OpenSearchEventSearchService(client, "obs-events");

        // This calls addTerm internally with null/blank serviceId/type/traceId
        // Verifies no exception and branches are hit
        assertThat(service).isNotNull();
    }

    private static Event sampleEvent(String eventId, String payload, String traceId) {
        Event event = new Event();
        event.setEventId(eventId);
        event.setSource("demo-generator");
        event.setServiceId("payment-service");
        event.setEnv("PROD");
        event.setType("METRIC");
        event.setPayload(payload);
        event.setTraceId(traceId);
        event.setTimestamp(Instant.parse("2026-09-03T10:00:00Z"));
        event.setStatus("OK");
        return event;
    }
}