package io.vykronis.remediation;

import io.vykronis.remediation.executor.ComposeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Binds the compose target (base file + apps overlay) from configuration into
 * a {@link ComposeOptions} used by the executor.
 */
@Configuration
public class RemediationComposeConfig {

    @Bean
    public ComposeOptions composeOptions(@Value("${remediation.compose.files}") String files) {
        List<String> composeFiles = Arrays.stream(files.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new ComposeOptions(composeFiles);
    }
}