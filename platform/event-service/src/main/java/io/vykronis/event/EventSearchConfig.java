package io.vykronis.event;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Low-level OpenSearch REST client used by {@link OpenSearchEventSearchService}.
 * Pointed at the environment via {@code VYKRONIS_OPENSEARCH_URL} (local default
 * matches the {@code search} compose profile).
 */
@Configuration
public class EventSearchConfig {

    @Value("${vykronis.search.url:http://localhost:9200}")
    private String url;

    @Bean(destroyMethod = "close")
    public RestClient opensearchRestClient() {
        String normalized = url.startsWith("http://") || url.startsWith("https://")
                ? url
                : "http://" + url;
        return RestClient.builder(HttpHost.create(normalized)).build();
    }
}