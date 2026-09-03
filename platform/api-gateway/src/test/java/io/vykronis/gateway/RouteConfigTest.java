package io.vykronis.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RouteConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    private List<Route> routes() {
        return routeLocator.getRoutes().collectList().block();
    }

    private boolean matches(Route route, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build());
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
    }

    @Test
    void incidentServiceRouteMatchesIncidentsPath() {
        List<Route> found = routes().stream()
                .filter(r -> matches(r, "/api/incidents/123"))
                .toList();
        assertThat(found).isNotEmpty();
    }

    @Test
    void incidentServiceRoutePointsToIncidentService() {
        Route incidentRoute = routes().stream()
                .filter(r -> matches(r, "/api/incidents/123"))
                .findFirst()
                .orElseThrow();
        URI uri = incidentRoute.getUri();
        assertThat(uri.getScheme()).isEqualTo("lb");
        assertThat(uri.getHost()).isEqualTo("incident-service");
    }

    @Test
    void incidentRouteDoesNotCaptureUnrelatedPaths() {
        Route incidentRoute = routes().stream()
                .filter(r -> matches(r, "/api/incidents/123"))
                .findFirst()
                .orElseThrow();
        assertThat(matches(incidentRoute, "/api/ingest/events")).isFalse();
        assertThat(matches(incidentRoute, "/api/incidentz/123")).isFalse();
    }
}
