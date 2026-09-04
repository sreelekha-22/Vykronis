package io.vykronis.contracts.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * A chunk of a JFR recording streamed from a service, persisted on
 * {@code obs.jfr}. Kafka keyed by {@code serviceId} so chunks of one service
 * stay ordered and can be reassembled into a continuous recording.
 *
 * @param id          unique record id (consumer idempotency)
 * @param serviceId   logical service that produced the chunk (Kafka partition key)
 * @param env         environment the chunk originated from
 * @param fileName    target file name inside the recording, e.g. {@code allocated-1.jfr}
 * @param content     binary JFR chunk payload
 * @param recordedAt  when the chunk was recorded
 */
public record JfrRecord(
        UUID id,
        String serviceId,
        Env env,
        String fileName,
        byte[] content,
        Instant recordedAt
) {
    public JfrRecord {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (serviceId == null) {
            throw new IllegalArgumentException("serviceId must not be null");
        }
        if (env == null) {
            throw new IllegalArgumentException("env must not be null");
        }
        if (fileName == null) {
            throw new IllegalArgumentException("fileName must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        if (recordedAt == null) {
            throw new IllegalArgumentException("recordedAt must not be null");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JfrRecord other)) {
            return false;
        }
        return id.equals(other.id)
                && serviceId.equals(other.serviceId)
                && env == other.env
                && fileName.equals(other.fileName)
                && Arrays.equals(content, other.content)
                && recordedAt.equals(other.recordedAt);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + serviceId.hashCode();
        result = 31 * result + env.hashCode();
        result = 31 * result + fileName.hashCode();
        result = 31 * result + Arrays.hashCode(content);
        result = 31 * result + recordedAt.hashCode();
        return result;
    }
}