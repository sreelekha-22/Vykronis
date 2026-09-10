package io.vykronis.orchestrator.demo;

import io.vykronis.contracts.model.ObservabilityEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DemoRunnerTest {

    @Test
    void scheduledTicksEmitOneEventEachToTheMetricsTopic() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, ObservabilityEvent> template = mock(KafkaTemplate.class);
        DemoRunner runner = new DemoRunner(template);

        for (int i = 0; i < 24; i++) {
            runner.tick();
        }

        ArgumentCaptor<ObservabilityEvent> events = ArgumentCaptor.forClass(ObservabilityEvent.class);
        verify(template, atLeastOnce()).send(org.mockito.ArgumentMatchers.eq("obs.metrics"),
                org.mockito.ArgumentMatchers.anyString(), events.capture());

        assertThat(events.getAllValues()).hasSize(24);
        // Iteration 1..6 (first burst) targets payment-service; later ticks rotate
        // through the four demo services. Spot-check: the very first event hits
        // payment-service (BURST_LENGTH=6, BURST_EVERY=12).
        assertThat(events.getAllValues().get(0).serviceId()).isEqualTo("payment-service");
    }
}