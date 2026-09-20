package io.vykronis.correlation;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * Self-healing topic bootstrap.
 *
 * <p>The streams topology uses a {@code GlobalKTable} over {@code obs.deployments},
 * which requires that topic to exist with a known partition count BEFORE the
 * streams instance starts (GlobalStreamThread does a metadata lookup at boot).
 * Broker {@code auto.create.topics} can't help here because the first write only
 * happens after the demo seeds a deployment — a chicken-and-egg that crashed the
 * engine whenever {@code obs.deployments} was missing (e.g. a clean Kafka data
 * volume). We therefore create the topology's source/sink topics up front with an
 * AdminClient. {@code @PostConstruct} runs during singleton pre-instantiation,
 * strictly before the streams {@code SmartLifecycle} starts.</p>
 */
@Component
public class TopicBootstrap {

    static final int PARTITIONS = 3;
    static final short REPLICATION = 1;

    private final String bootstrapServers;

    public TopicBootstrap(@Value("${kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @PostConstruct
    void ensureTopicsExist() {
        java.util.concurrent.TimeUnit unit = java.util.concurrent.TimeUnit.SECONDS;
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            // Kafka may still be starting up (compose health-gates the container,
            // but a freshly recreated broker can briefly answer metadata with an
            // empty cluster). Give the listTopics call a bounded retry loop.
            java.util.Set<String> existing = null;
            int attempts = 0;
            while (existing == null && attempts < 5) {
                try {
                    existing = admin.listTopics().names().get(30, unit);
                } catch (java.util.concurrent.TimeoutException e) {
                    attempts++;
                    if (attempts == 5) {
                        throw e;
                    }
                    try {
                        Thread.sleep(5_000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting for Kafka", ie);
                    }
                }
            }
            final java.util.Set<String> found = existing;
            List<NewTopic> missing = topics().stream()
                    .filter(t -> !found.contains(t.name()))
                    .toList();
            if (!missing.isEmpty()) {
                admin.createTopics(missing).all().get(30, unit);
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to bootstrap Kafka topics for correlation-engine", e);
        }
    }

    private List<NewTopic> topics() {
        return List.of(
                new NewTopic(CorrelationTopology.METRICS_TOPIC, PARTITIONS, REPLICATION),
                new NewTopic(CorrelationTopology.DEPLOYMENTS_TOPIC, PARTITIONS, REPLICATION),
                new NewTopic(CorrelationTopology.ALERTS_TOPIC, PARTITIONS, REPLICATION));
    }
}