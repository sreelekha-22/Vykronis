package io.vykronis.demo;

import io.vykronis.contracts.Topics;
import io.vykronis.contracts.model.JfrRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JfrStreamingExporterTest {

    @Mock
    private KafkaTemplate<String, JfrRecord> kafkaTemplate;
    @Mock
    private JfrCaptureService captureService;

    private JfrStreamingExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new JfrStreamingExporter(kafkaTemplate, captureService);
    }

    @Test
    void emitChunkSendsJfrRecordToObsJfrKeyedByPaymentService() throws Exception {
        byte[] chunk = {0x46, 0x4C, 0x52, 0x3C};
        when(captureService.captureChunk()).thenReturn(chunk);

        exporter.emitChunk();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JfrRecord> record = ArgumentCaptor.forClass(JfrRecord.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), record.capture());

        assertThat(topic.getValue()).isEqualTo(Topics.JFR);
        assertThat(key.getValue()).isEqualTo(JfrStreamingExporter.SERVICE_ID);
        assertThat(record.getValue().serviceId()).isEqualTo(JfrStreamingExporter.SERVICE_ID);
        assertThat(record.getValue().content()).isEqualTo(chunk);
        assertThat(record.getValue().fileName()).endsWith(".jfr");
    }
}
