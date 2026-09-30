package edu.hm.hafner.grading.github;

import static edu.hm.hafner.grading.github.QualityMonitor.REFERENCE_REPORTS;
import static org.assertj.core.api.Assertions.assertThat;

import edu.hm.hafner.util.FilteredLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualityMonitorTest {
    @Test
    void shouldCreateInstance() {
        var qualityMonitor = new QualityMonitor();

        assertThat(qualityMonitor.getDefaultConfigurationPath()).isEqualTo("/default-no-score-config.json");
    }

    @Test
    void shouldCheckReferenceReports() throws IOException {
        var qualityMonitor = new QualityMonitor();

        var log = new FilteredLog();

        assertThat(qualityMonitor.fetchDeltaReportsFromPreviousPipeline(log)).isEmpty();

        var referenceReports = Path.of(REFERENCE_REPORTS);
        Files.createDirectory(referenceReports).toFile().deleteOnExit();

        assertThat(qualityMonitor.fetchDeltaReportsFromPreviousPipeline(log)).contains(referenceReports);
    }
}
