package io.vykronis.orchestrator.demo;

import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.Env;
import io.vykronis.contracts.model.JfrRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Streams JFR chunks captured from the JVM onto {@link Topics#JFR obs.jfr}, so
 * the observability stack has genuine GC/allocation evidence per service. Gated
 * behind the {@code jfr-stream} profile so it does not run on every boot.
 *
 * <p>Chunks are keyed by {@code serviceId} so they stay ordered per service and
 * can be reassembled into a continuous recording downstream.</p>
 */
@Component
@Profile("jfr-stream")
public class JfrStreamingExporter {

    public static final String SERVICE_ID = "payment-service";

    private final KafkaTemplate<String, JfrRecord> kafkaTemplate;
    private final JfrCaptureService captureService;

    public JfrStreamingExporter(
            @org.springframework.beans.factory.annotation.Qualifier("jfrKafkaTemplate")
            KafkaTemplate<String, JfrRecord> kafkaTemplate,
            JfrCaptureService captureService) {
        this.kafkaTemplate = kafkaTemplate;
        this.captureService = captureService;
    }

    @Scheduled(fixedRate = 10_000)
    public void emitChunk() {
        byte[] chunk = captureService.captureChunk();
        JfrRecord record = new JfrRecord(
                UUID.randomUUID(),
                SERVICE_ID,
                Env.PROD,
                "allocated-" + System.currentTimeMillis() + ".jfr",
                chunk,
                Instant.now());
        kafkaTemplate.send(Topics.JFR, SERVICE_ID, record);
    }
}