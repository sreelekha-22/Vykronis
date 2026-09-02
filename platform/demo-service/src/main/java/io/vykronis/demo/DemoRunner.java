package io.vykronis.demo;

import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.EventType;
import io.vykronis.contracts.model.ObservabilityEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class DemoRunner {

    private static final String[] SERVICE_IDS = {"order-service", "payment-service", "inventory-service", "notification-service"};

    @Autowired
    private KafkaTemplate<String, ObservabilityEvent> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void generateTraffic() {
        new Thread(() -> {
            int counter = 0;
            while (true) {
                try {
                    Thread.sleep(500);
                    counter++;
                    if (counter % 10 == 0) {
                        String serviceId = SERVICE_IDS[(counter / 10) % SERVICE_IDS.length];
                        var payload = objectMapper.createObjectNode()
                            .put("error", "Error burst detected")
                            .put("service", serviceId);
                        ObservabilityEvent event = new ObservabilityEvent(
                            UUID.randomUUID(),
                            java.time.Instant.now(),
                            "demo-generator",
                            serviceId,
                            Env.PROD,
                            EventType.METRIC,
                            payload,
                            "demo-trace-" + counter
                        );
                        kafkaTemplate.send("obs.metrics", serviceId, event);
                        System.out.println("Sent observability event: " + event.id());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
}