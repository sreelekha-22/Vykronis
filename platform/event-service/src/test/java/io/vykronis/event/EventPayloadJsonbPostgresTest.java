package io.vykronis.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code payload} column is {@code jsonb} on Postgres. Without an explicit
 * JSON mapping Hibernate binds the String as {@code varchar} and Postgres
 * rejects the write ({@code column "payload" is of type jsonb but expression
 * is of type character varying}). H2 ignores the column definition, so only a
 * real Postgres proves the round-trip (RED first).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Transactional
class EventPayloadJsonbPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private EventRepository repository;

    @Test
    void jsonPayloadRoundTripsThroughRealPostgresJsonb() {
        Event event = new Event(
                "evt-jsonb-1",
                "payment-service",
                "payment-service",
                "PROD",
                "METRIC",
                "{\"error_rate\":25.0,\"error_count\":15}",
                "trace-1",
                Instant.parse("2026-09-03T10:00:00Z"));
        repository.saveAndFlush(event);

        Event found = repository.findByEventId("evt-jsonb-1");

        assertThat(found).isNotNull();
        assertThat(found.getPayload()).contains("\"error_rate\":25.0");
    }
}
