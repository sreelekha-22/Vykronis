package io.vykronis.event;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vykronis.common.json.Json;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ensures the event search index exists with an explicit mapping (keyword
 * fields for service/type/trace filters, a date field for the incident window
 * range query). Runs at startup; if OpenSearch is unreachable the service keeps
 * running — Postgres remains the source of truth and the index is optional.
 */
@Component
public class EventIndexBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EventIndexBootstrap.class);

    private final RestClient client;
    private final String index;

    public EventIndexBootstrap(
            RestClient opensearchRestClient,
            @Value("${vykronis.search.index:obs-events}") String index) {
        this.client = opensearchRestClient;
        this.index = index;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (indexExists()) {
                return;
            }
            Request request = new Request("PUT", "/" + index);
            request.setJsonEntity(body().toString());
            client.performRequest(request);
        } catch (IOException e) {
            log.warn("OpenSearch unavailable; event index bootstrap skipped: {}", e.getMessage());
        }
    }

    private boolean indexExists() throws IOException {
        Response response = client.performRequest(new Request("HEAD", "/" + index));
        return response.getStatusLine().getStatusCode() == 200;
    }

    private ObjectNode body() {
        ObjectNode properties = Json.mapper().createObjectNode();
        properties.putObject("eventId").put("type", "keyword");
        properties.putObject("source").put("type", "keyword");
        properties.putObject("serviceId").put("type", "keyword");
        properties.putObject("env").put("type", "keyword");
        properties.putObject("type").put("type", "keyword");
        properties.putObject("payload").put("type", "object");
        properties.putObject("traceId").put("type", "keyword");
        properties.putObject("timestamp").put("type", "date");
        properties.putObject("status").put("type", "keyword");
        ObjectNode mappings = Json.mapper().createObjectNode();
        mappings.set("properties", properties);
        return Json.mapper().createObjectNode().set("mappings", mappings);
    }
}