package io.vykronis.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("incident-service", r -> r.path("/api/incidents/**")
                .uri("http://incident-service:8084"))
            .route("event-service", r -> r.path("/api/search/**")
                .uri("http://event-service:8082"))
            .build();
    }
}