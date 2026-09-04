package io.vykronis.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = TestObservabilityApplication.class, properties = {
        "spring.application.name=common-observability-test",
        "vykronis.observability.env=test",
        "otel.sdk.disabled=true"
})
class ObservabilityAutoConfigurationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PrometheusMeterRegistry prometheusRegistry;

    @Autowired
    private OtlpMeterRegistry otlpRegistry;

    @Test
    void bootstrapsCompositeMeterRegistryWithPrometheusAndOtlp() {
        assertThat(meterRegistry).isInstanceOf(CompositeMeterRegistry.class);
        assertThat(CompositeMeterRegistry.class.cast(meterRegistry).getRegistries())
                .anyMatch(registry -> registry instanceof PrometheusMeterRegistry)
                .anyMatch(registry -> registry instanceof OtlpMeterRegistry);
        assertThat(prometheusRegistry).isNotNull();
        assertThat(otlpRegistry).isNotNull();
    }

    @Test
    void appliesCommonApplicationAndEnvironmentTags() {
        Counter counter = Counter.builder("vykronis.test.counter").register(prometheusRegistry);
        assertThat(counter.getId().getTags())
                .contains(Tag.of("application", "common-observability-test"), Tag.of("env", "test"));
        Counter otlpCounter = Counter.builder("vykronis.test.counter").register(otlpRegistry);
        assertThat(otlpCounter.getId().getTags())
                .contains(Tag.of("application", "common-observability-test"), Tag.of("env", "test"));
    }

    @Test
    void rendersPrometheusScrapeOutput() {
        assertThat(prometheusRegistry.scrape()).isNotBlank();
    }
}