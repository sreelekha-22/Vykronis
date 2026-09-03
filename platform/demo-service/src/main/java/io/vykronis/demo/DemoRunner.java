package io.vykronis.demo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vykronis.common.json.Json;
import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.EventType;
import io.vykronis.contracts.model.ObservabilityEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates demo traffic onto {@code obs.metrics} for the four demo services.
 *
 * <p>Auto-generation is gated behind the {@code demo-traffic} profile so the
 * service does not spam metrics on every boot. Most iterations are healthy (low
 * error rate). Every {@code BURST_EVERY} iterations it emits an error burst — a
 * stretch of elevated {@code error_rate} — for {@code payment-service}. The
 * correlation engine windows these and, when the error rate breaches the
 * threshold, emits an incident candidate.</p>
 */
@Component
@Profile("demo-traffic")
public class DemoRunner {

    private static final String[] SERVICE_IDS =
            {"order-service", "payment-service", "inventory-service", "notification-service"};

    private static final int BURST_EVERY = 12;
    private static final int BURST_LENGTH = 6;

    private final KafkaTemplate<String, ObservabilityEvent> kafkaTemplate;
    private final AtomicInteger counter = new AtomicInteger(0);

    public DemoRunner(KafkaTemplate<String, ObservabilityEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedRate = 500)
    public void tick() {
        emit(counter.incrementAndGet());
    }

    private void emit(int n) {
        int burstStart = ((n - 1) / BURST_EVERY) * BURST_EVERY + 1;
        boolean inBurst = n - burstStart < BURST_LENGTH;
        String target = inBurst ? "payment-service" : SERVICE_IDS[(n - 1) % SERVICE_IDS.length];

        double errorRate;
        long errorCount;
        if (inBurst) {
            errorRate = 45.0 + ThreadLocalRandom.current().nextDouble(0, 20); // 45-65%
            errorCount = ThreadLocalRandom.current().nextInt(3, 9);
        } else {
            errorRate = 0.5 + ThreadLocalRandom.current().nextDouble(0, 3);  // ~0.5-3.5%
            errorCount = 0;
        }

        ObjectNode payload = Json.mapper().createObjectNode()
                .put("error_rate", errorRate)
                .put("error_count", errorCount)
                .put("latency_ms", healthyLatency(inBurst));

        ObservabilityEvent event = new ObservabilityEvent(
                UUID.randomUUID(),
                Instant.now(),
                "demo-generator",
                target,
                Env.PROD,
                EventType.METRIC,
                payload,
                "demo-trace-" + n);

        kafkaTemplate.send("obs.metrics", target, event);
    }

    private double healthyLatency(boolean inBurst) {
        return inBurst
                ? 900.0 + ThreadLocalRandom.current().nextDouble(0, 600)
                : 40.0 + ThreadLocalRandom.current().nextDouble(0, 80);
    }
}
