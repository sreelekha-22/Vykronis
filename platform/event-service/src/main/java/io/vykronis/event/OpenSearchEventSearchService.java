package io.vykronis.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vykronis.common.json.Json;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link EventSearchService} backed by OpenSearch. Indexes each persisted event
 * as a JSON document (keyed by {@code eventId}) and answers windowed searches
 * with a timestamp range filter plus optional service/type/trace filters.
 */
@Service
public class OpenSearchEventSearchService implements EventSearchService {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchEventSearchService.class);

    private final RestClient client;
    private final String index;

    public OpenSearchEventSearchService(
            RestClient opensearchRestClient,
            @Value("${vykronis.search.index:obs-events}") String index) {
        this.client = opensearchRestClient;
        this.index = index;
    }

    @Override
    public void index(Event event) {
        ObjectNode doc = Json.mapper().createObjectNode();
        doc.put("eventId", event.getEventId());
        doc.put("source", event.getSource());
        doc.put("serviceId", event.getServiceId());
        doc.put("env", event.getEnv());
        doc.put("type", event.getType());
        doc.set("payload", payloadNode(event.getPayload()));
        doc.put("traceId", event.getTraceId());
        doc.put("timestamp", event.getTimestamp().toString());
        doc.put("status", event.getStatus());
        try {
            Request request = new Request("PUT", "/" + index + "/_doc/"
                    + URLEncoder.encode(event.getEventId(), StandardCharsets.UTF_8));
            request.setJsonEntity(doc.toString());
            request.addParameter("refresh", "wait_for");
            client.performRequest(request);
        } catch (IOException e) {
            log.warn("Skipped indexing event {}; OpenSearch unavailable: {}", event.getEventId(), e.getMessage());
        }
    }

    private JsonNode payloadNode(String payload) {
        if (payload == null) {
            return Json.mapper().nullNode();
        }
        try {
            return Json.mapper().readTree(payload);
        } catch (JsonProcessingException e) {
            return Json.mapper().getNodeFactory().textNode(payload);
        }
    }

    @Override
    public List<SearchHit> search(Instant from, Instant to, String serviceId, String type, String traceId) {
        ObjectNode body = Json.mapper().createObjectNode();
        ObjectNode bool = body.putObject("query").putObject("bool");
        ArrayNode filter = bool.putArray("filter");
        ObjectNode range = filter.addObject().putObject("range").putObject("timestamp");
        range.put("gte", from.toString());
        range.put("lte", to.toString());
        addTerm(filter, "serviceId", serviceId);
        addTerm(filter, "type", type);
        addTerm(filter, "traceId", traceId);
        body.putObject("sort").putObject("timestamp").put("order", "asc");
        body.put("size", 500);

        try {
            Request request = new Request("POST", "/" + index + "/_search");
            request.setJsonEntity(body.toString());
            Response response = client.performRequest(request);
            return toHits(Json.mapper().readTree(response.getEntity().getContent()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to search indexed events", e);
        }
    }

    private List<SearchHit> toHits(JsonNode root) {
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) {
            JsonNode source = hit.path("_source");
            String payload = source.path("payload").isMissingNode() || source.path("payload").isNull()
                    ? null
                    : source.path("payload").toString();
            hits.add(new SearchHit(
                    source.path("eventId").asText(null),
                    source.path("source").asText(null),
                    source.path("serviceId").asText(null),
                    source.path("env").asText(null),
                    source.path("type").asText(null),
                    payload,
                    source.path("traceId").asText(null),
                    Instant.parse(source.path("timestamp").asText()),
                    source.path("status").asText(null)));
        }
        return hits;
    }

    private void addTerm(ArrayNode filter, String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        filter.addObject().putObject("term").put(field, value);
    }
}