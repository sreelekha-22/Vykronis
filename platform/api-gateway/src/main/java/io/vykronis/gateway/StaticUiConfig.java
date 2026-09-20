package io.vykronis.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class StaticUiConfig {

    @Bean
    RouterFunction<ServerResponse> staticUi() {
        return RouterFunctions.route(
                GET("/").or(GET("/ui").or(GET("/ui/**"))),
                request -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(BodyInserters.fromValue(indexPage())));
    }

    private Resource indexPage() {
        return new ClassPathResource("static/index.html");
    }
}