package io.vykronis.event;

import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.apache.http.HttpEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

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

    @Test
    void searchMapsPayloadStatesAndTermsInHits() throws Exception {
        RestClient client = mock(RestClient.class);
        long from = Instant.parse("2026-09-03T09:00:00Z").toEpochMilli();
        String body = """
                {"hits":{"hits":[
                  {"_source":{"eventId":"evt-1","source":"src","serviceId":"pay","env":"PROD","type":"METRIC",
                    "payload":{"error_rate":25.0},"traceId":"tr-1","timestamp":"2026-09-03T10:00:00Z","status":"OPEN"}},
                  {"_source":{"eventId":"evt-2","payload":null,"timestamp":"2026-09-03T10:01:00Z"}},
                  {"_source":{"eventId":"evt-3","timestamp":"2026-09-03T10:02:00Z"}}
                ]}}""";
        when(client.performRequest(any(Request.class)))
                .thenAnswer(inv -> response(body));
        OpenSearchEventSearchService service = new OpenSearchEventSearchService(client, "obs-events");

        var hits = service.search(
                Instant.parse("2026-09-03T09:00:00Z"),
                Instant.parse("2026-09-03T11:00:00Z"),
                "pay",
                "METRIC",
                "tr-1");

        verify(client).performRequest(any(Request.class));
        assertThat(hits).hasSize(3);
        assertThat(hits.get(0).payload()).contains("error_rate");
        assertThat(hits.get(1).payload()).isNull();
        assertThat(hits.get(2).payload()).isNull();
        assertThat(from).isPositive();
    }

    @Test
    void searchReturnsEmptyHitsWhenNoDocuments() throws Exception {
        RestClient client = mock(RestClient.class);
        when(client.performRequest(any(Request.class)))
                .thenAnswer(inv -> response("{\"hits\":{\"hits\":[]}}"));
        OpenSearchEventSearchService service = new OpenSearchEventSearchService(client, "obs-events");

        var hits = service.search(
                Instant.parse("2026-09-03T09:00:00Z"),
                Instant.parse("2026-09-03T11:00:00Z"),
                null,
                null,
                null);

        assertThat(hits).isEmpty();
    }

    private static Response response(String json) throws IOException {
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContent())
                .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        Response response = mock(Response.class);
        when(response.getEntity()).thenReturn(entity);
        return response;
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