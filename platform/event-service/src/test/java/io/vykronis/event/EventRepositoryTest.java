package io.vykronis.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class EventRepositoryTest {

    @Autowired
    private EventRepository repository;

    private Event sampleEvent() {
        return new Event(
                "evt-001",
                "payment-service",
                "payment-service",
                "PROD",
                "METRIC",
                "{\"value\":1}",
                "trace-1",
                Instant.parse("2026-09-03T10:00:00Z"));
    }

    @Test
    void savesAndRetrievesEventByEventId() {
        repository.save(sampleEvent());

        Event found = repository.findByEventId("evt-001");
        assertThat(found).isNotNull();
        assertThat(found.getEventId()).isEqualTo("evt-001");
        assertThat(found.getSource()).isEqualTo("payment-service");
        assertThat(found.getServiceId()).isEqualTo("payment-service");
        assertThat(found.getEnv()).isEqualTo("PROD");
        assertThat(found.getType()).isEqualTo("METRIC");
        assertThat(found.getTimestamp()).isEqualTo(Instant.parse("2026-09-03T10:00:00Z"));
        assertThat(found.getTraceId()).isEqualTo("trace-1");
    }

    @Test
    void defaultSStatusIsOpen() {
        Event saved = repository.save(sampleEvent());
        assertThat(saved.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void canUpdateStatusOnDetectedEvent() {
        repository.save(sampleEvent());
        Event loaded = repository.findByEventId("evt-001");
        loaded.setStatus("RESOLVED");
        repository.save(loaded);

        Event reloaded = repository.findByEventId("evt-001");
        assertThat(reloaded.getStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void existsByEventIdReflectsPresence() {
        assertThat(repository.existsByEventId("evt-001")).isFalse();
        repository.save(sampleEvent());
        assertThat(repository.existsByEventId("evt-001")).isTrue();
    }

    @Test
    void uniqueEventIdPreventsDuplicatePersistence() {
        repository.saveAndFlush(sampleEvent());
        assertThatThrownBy(() -> repository.saveAndFlush(sampleEvent()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
