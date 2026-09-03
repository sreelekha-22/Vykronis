package io.vykronis.correlation;

import io.vykronis.contracts.model.ObservabilityEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

/**
 * Uses the event's own {@code timestamp} (wall-clock metric time) as the event
 * time for windowing, rather than the Kafka record timestamp. This keeps
 * windows aligned with when the telemetry actually happened.
 */
public class MetricTimestampExtractor implements TimestampExtractor {
    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        Object value = record.value();
        if (value instanceof ObservabilityEvent evt && evt.timestamp() != null) {
            return evt.timestamp().toEpochMilli();
        }
        return partitionTime;
    }
}
