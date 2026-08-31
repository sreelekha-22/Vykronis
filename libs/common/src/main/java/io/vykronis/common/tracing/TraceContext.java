package io.vykronis.common.tracing;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Minimal W3C-style trace context helpers. A full OpenTelemetry / Micrometer
 * bridge arrives in Phase 3; until then services share a plain trace id header.
 */
public final class TraceContext {

    /** Header name used to propagate the correlation trace id across services. */
    public static final String TRACE_ID_HEADER = "X-Vykronis-Trace-Id";

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceContext() {
    }

    /** Generates a 16-byte (128-bit) random trace id, hex-encoded (32 chars). */
    public static String newTraceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
