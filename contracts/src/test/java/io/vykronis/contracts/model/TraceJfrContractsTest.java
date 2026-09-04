package io.vykronis.contracts.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceJfrContractsTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void traceSpanRoundTrips() throws Exception {
        TraceSpan span = new TraceSpan(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "trace-1",
                "payment-service",
                Env.PROD,
                "checkout",
                Instant.parse("2026-09-04T10:05:00Z"),
                120L,
                TraceStatus.ERROR,
                "parent-9",
                Map.of("http.method", "POST", "http.status", "500"));

        String json = mapper.writeValueAsString(span);
        TraceSpan parsed = mapper.readValue(json, TraceSpan.class);

        assertThat(parsed).isEqualTo(span);
        assertThat(parsed.traceId()).isEqualTo("trace-1");
        assertThat(parsed.status()).isEqualTo(TraceStatus.ERROR);
        assertThat(parsed.durationMs()).isEqualTo(120L);
        assertThat(parsed.parentSpanId()).isEqualTo("parent-9");
    }

    @Test
    void traceSpanRejectsNullsAndNegativeDuration() {
        UUID id = UUID.randomUUID();
        Instant start = Instant.parse("2026-09-04T10:05:00Z");

        assertThatThrownBy(() -> new TraceSpan(null, "t", "s", Env.PROD, "n", start, 1, TraceStatus.OK, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceSpan(id, null, "s", Env.PROD, "n", start, 1, TraceStatus.OK, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceSpan(id, "t", null, Env.PROD, "n", start, 1, TraceStatus.OK, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceSpan(id, "t", "s", null, "n", start, 1, TraceStatus.OK, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceSpan(id, "t", "s", Env.PROD, null, start, 1, TraceStatus.OK, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceSpan(id, "t", "s", Env.PROD, "n", null, 1, TraceStatus.OK, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceSpan(id, "t", "s", Env.PROD, "n", start, -1, TraceStatus.OK, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceSpan(id, "t", "s", Env.PROD, "n", start, 1, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceSpan(id, "t", "s", Env.PROD, "n", start, 1, TraceStatus.OK, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void traceSpanAllowsNullParentSpanAndEmptyAttributes() {
        TraceSpan span = new TraceSpan(
                UUID.randomUUID(), "t", "s", Env.DEV, "n",
                Instant.parse("2026-09-04T10:05:00Z"), 0, TraceStatus.OK, null, Map.of());

        assertThat(span.parentSpanId()).isNull();
        assertThat(span.attributes()).isEmpty();
    }

    @Test
    void jfrRecordRoundTripsBinaryContent() throws Exception {
        JfrRecord jfr = new JfrRecord(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "demo-service",
                Env.DEV,
                "alloc-chunk-1.jfr",
                new byte[]{0x01, 0x02, 0x03, 0x04, 0x05},
                Instant.parse("2026-09-04T10:06:00Z"));

        String json = mapper.writeValueAsString(jfr);
        JfrRecord parsed = mapper.readValue(json, JfrRecord.class);

        assertThat(parsed).isEqualTo(jfr);
        assertThat(parsed.content()).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05);
        assertThat(parsed.fileName()).isEqualTo("alloc-chunk-1.jfr");
    }

    @Test
    void jfrRecordEqualsAndHashCodeUseContentBytes() {
        JfrRecord a = new JfrRecord(
                UUID.randomUUID(), "demo-service", Env.DEV, "f.jfr",
                new byte[]{1, 2, 3}, Instant.parse("2026-09-04T10:06:00Z"));
        JfrRecord b = new JfrRecord(
                a.id(), "demo-service", Env.DEV, "f.jfr",
                new byte[]{1, 2, 3}, Instant.parse("2026-09-04T10:06:00Z"));
        JfrRecord c = new JfrRecord(
                a.id(), "demo-service", Env.DEV, "f.jfr",
                new byte[]{1, 2, 4}, Instant.parse("2026-09-04T10:06:00Z"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void jfrRecordRejectsNullOrEmptyContent() {
        UUID id = UUID.randomUUID();
        Instant at = Instant.parse("2026-09-04T10:06:00Z");

        assertThatThrownBy(() -> new JfrRecord(null, "s", Env.DEV, "f.jfr", new byte[]{1}, at))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JfrRecord(id, null, Env.DEV, "f.jfr", new byte[]{1}, at))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JfrRecord(id, "s", null, "f.jfr", new byte[]{1}, at))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JfrRecord(id, "s", Env.DEV, null, new byte[]{1}, at))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JfrRecord(id, "s", Env.DEV, "f.jfr", null, at))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JfrRecord(id, "s", Env.DEV, "f.jfr", new byte[]{}, at))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JfrRecord(id, "s", Env.DEV, "f.jfr", new byte[]{1}, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}