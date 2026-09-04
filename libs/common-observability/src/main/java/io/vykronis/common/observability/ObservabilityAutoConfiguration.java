package io.vykronis.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnClass(MeterRegistry.class)
public class ObservabilityAutoConfiguration {

    public static final String APPLICATION_TAG = "application";
    public static final String ENVIRONMENT_TAG = "env";

    @Bean
    @ConditionalOnMissingBean(name = "vykronisCommonTagsCustomizer")
    MeterRegistryCustomizer<MeterRegistry> vykronisCommonTagsCustomizer(ObservabilityProperties properties,
                                                                        Environment environment) {
        return registry -> {
            String application = environment.getProperty("spring.application.name", "unknown");
            registry.config().commonTags(List.of(
                    Tag.of(APPLICATION_TAG, application),
                    Tag.of(ENVIRONMENT_TAG, properties.getEnv())));
        };
    }
}