package edu.hm.hafner.grading.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QualityMonitorTest {
    @Test
    void shouldCreateInstance() {
        var qualityMonitor = new QualityMonitor();

        assertThat(qualityMonitor.getDefaultConfigurationPath()).isEqualTo("/default-no-score-config.json");
    }
}
