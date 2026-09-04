package io.vykronis.demo;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Captures a short, real JFR recording chunk from the JVM's built-in Flight
 * Recorder. Used by the demo service to stream genuine GC/allocation evidence
 * onto {@code obs.jfr}, which the timeline can later surface.
 *
 * <p>Each call runs a temporary recording against the default profile for a
 * brief {@link #DURATION}, dumps the recording to a temp file, and returns its
 * raw bytes. The chunk is a self-contained JFR file (begins with the {@code FLR}
 * magic header).</p>
 */
@Service
public class JfrCaptureService {

    /** How long each capture window runs, so a chunk has meaningful GC data. */
    private static final Duration DURATION = Duration.ofMillis(500);

    /**
     * Records a JFR chunk using the default {@code Configuration} (enables
     * GC/heap/thread events) and returns the serialized recording bytes.
     *
     * @return non-empty JFR binary chunk beginning with the {@code FLR} magic
     */
    public byte[] captureChunk() {
        try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
            recording.setToDisk(true);
            recording.setMaxAge(DURATION);
            recording.start();
            Thread.sleep(DURATION.toMillis());

            Path tmp = Files.createTempFile("vykronis-jfr-chunk", ".jfr");
            recording.dump(tmp);

            byte[] content = Files.readAllBytes(tmp);
            Files.deleteIfExists(tmp);

            if (content.length == 0) {
                throw new IllegalStateException("JFR capture produced an empty chunk");
            }
            return content;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("JFR capture interrupted", e);
        } catch (IOException | java.text.ParseException e) {
            throw new IllegalStateException("JFR capture failed", e);
        }
    }
}
