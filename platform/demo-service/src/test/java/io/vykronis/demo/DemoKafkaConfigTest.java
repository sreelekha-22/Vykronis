package io.vykronis.demo;

import io.vykronis.contracts.model.JfrRecord;
import io.vykronis.contracts.model.ObservabilityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class DemoKafkaConfigTest {

    private DemoKafkaConfig config;

    @BeforeEach
    void setUp() throws Exception {
        config = new DemoKafkaConfig();
        setField(config, "bootstrapServers", "localhost:9092");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = DemoKafkaConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void observabilityProducerFactoryProducesAConfiguredFactory() {
        ProducerFactory<String, ObservabilityEvent> factory = config.demoProducerFactory();

        assertThat(factory).isNotNull();
    }

    @Test
    void observabilityKafkaTemplateProducesATemplateWithAProducerFactory() {
        KafkaTemplate<String, ObservabilityEvent> template = config.observabilityKafkaTemplate();

        assertThat(template).isNotNull();
        assertThat(template.getProducerFactory()).isNotNull();
    }

    @Test
    void jfrProducerFactoryProducesAConfiguredFactory() {
        ProducerFactory<String, JfrRecord> factory = config.jfrProducerFactory();

        assertThat(factory).isNotNull();
    }

    @Test
    void jfrKafkaTemplateProducesATemplateWithAProducerFactory() {
        KafkaTemplate<String, JfrRecord> template = config.jfrKafkaTemplate();

        assertThat(template).isNotNull();
        assertThat(template.getProducerFactory()).isNotNull();
    }

    @Test
    void stringKafkaTemplateProducesAStringToStringTemplate() {
        KafkaTemplate<String, String> template = config.stringKafkaTemplate();

        assertThat(template).isNotNull();
    }
}