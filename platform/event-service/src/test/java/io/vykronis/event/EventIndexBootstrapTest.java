package io.vykronis.event;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventIndexBootstrapTest {

    @Test
    void returnsEarlyWhenIndexAlreadyExists() throws Exception {
        RestClient client = mock(RestClient.class);
        when(client.performRequest(any(Request.class)))
                .thenAnswer(inv -> status(200));
        EventIndexBootstrap bootstrap = new EventIndexBootstrap(client, "obs-events");

        bootstrap.run(null);

        verify(client).performRequest(any(Request.class));
    }

    @Test
    void createsIndexWithMappingsWhenMissing() throws Exception {
        RestClient client = mock(RestClient.class);
        when(client.performRequest(any(Request.class))).thenAnswer(inv -> {
            Request request = inv.getArgument(0);
            if ("HEAD".equals(request.getMethod())) {
                return status(404);
            }
            assertThat(request.getMethod()).isEqualTo("PUT");
            assertThat(request.getEndpoint()).isEqualTo("/obs-events");
            HttpEntity entity = request.getEntity();
            assertThat(entity).isNotNull();
            String body = new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("\"mappings\"");
            return mock(Response.class);
        });
        EventIndexBootstrap bootstrap = new EventIndexBootstrap(client, "obs-events");

        bootstrap.run(null);

        ArgumentCaptor<Request> requests = ArgumentCaptor.forClass(Request.class);
        verify(client, times(2)).performRequest(requests.capture());
        assertThat(requests.getAllValues()).extracting(Request::getMethod)
                .containsExactly("HEAD", "PUT");
    }

    @Test
    void swallowsIOExceptionWhenSearchUnavailable() throws Exception {
        RestClient client = mock(RestClient.class);
        when(client.performRequest(any(Request.class))).thenThrow(new IOException("boom"));
        EventIndexBootstrap bootstrap = new EventIndexBootstrap(client, "obs-events");

        bootstrap.run(null);

        verify(client).performRequest(any(Request.class));
    }

    private static Response status(int code) {
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(code);
        Response response = mock(Response.class);
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }
}