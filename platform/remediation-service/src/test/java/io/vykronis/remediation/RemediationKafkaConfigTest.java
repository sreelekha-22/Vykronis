package io.vykronis.remediation;

import io.vykronis.contracts.model.RemediationCommand;
import io.vykronis.contracts.model.RemediationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class RemediationKafkaConfigTest {

    private RemediationKafkaConfig config;

    @BeforeEach
    void setUp() throws Exception {
        config = new RemediationKafkaConfig();
        setField(config, "bootstrapServers", "localhost:9092");
        setField(config, "groupId", "test-group");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = RemediationKafkaConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void commandConsumerFactoryProducesAConfiguredFactory() {
        ConsumerFactory<String, RemediationCommand> factory = config.remediationCommandConsumerFactory();

        assertThat(factory).isNotNull();
        assertThat(factory.getClass().getSimpleName()).contains("DefaultKafkaConsumerFactory");
    }

    @Test
    void listenerContainerFactoryWrapsTheConsumerFactory() {
        ConsumerFactory<String, RemediationCommand> cf = config.remediationCommandConsumerFactory();
        ConcurrentKafkaListenerContainerFactory<String, RemediationCommand> factory =
                config.kafkaListenerContainerFactory(cf);

        assertThat(factory).isNotNull();
        assertThat(factory.getConsumerFactory()).isSameAs(cf);
    }

    @Test
    void resultProducerFactoryProducesAConfiguredFactory() {
        ProducerFactory<String, RemediationResult> factory = config.remediationResultProducerFactory();

        assertThat(factory).isNotNull();
    }

    @Test
    void resultKafkaTemplateWrapsTheProducerFactory() {
        ProducerFactory<String, RemediationResult> pf = config.remediationResultProducerFactory();
        KafkaTemplate<String, RemediationResult> template = config.remediationResultKafkaTemplate(pf);

        assertThat(template).isNotNull();
        assertThat(template.getProducerFactory()).isSameAs(pf);
    }
}