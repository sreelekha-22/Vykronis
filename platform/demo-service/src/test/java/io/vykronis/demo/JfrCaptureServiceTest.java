package io.vykronis.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JfrCaptureServiceTest {

    private final JfrCaptureService captureService = new JfrCaptureService();

    @Test
    void captureChunkReturnsNonEmptyJfrBinary() {
        byte[] chunk = captureService.captureChunk();

        assertThat(chunk).isNotEmpty();
        assertThat((chunk[0] == 'F') && (chunk[1] == 'L') && (chunk[2] == 'R'))
                .as("captured bytes should be a valid JFR file (FLR magic)")
                .isTrue();
    }
}
