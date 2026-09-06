package io.vykronis.remediation;

import io.vykronis.remediation.executor.ComposeOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemediationComposeConfigTest {

    @Test
    void splitsAndTrimsCommaSeparatedFiles() {
        RemediationComposeConfig config = new RemediationComposeConfig();

        ComposeOptions options = config.composeOptions("infra/compose/a.yml, infra/compose/b.yml ,, ");

        assertThat(options.composeFiles()).containsExactly(
                "infra/compose/a.yml", "infra/compose/b.yml");
    }

    @Test
    void rejectsBlankInput() {
        RemediationComposeConfig config = new RemediationComposeConfig();

        assertThatThrownBy(() -> config.composeOptions("   ,  , "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
