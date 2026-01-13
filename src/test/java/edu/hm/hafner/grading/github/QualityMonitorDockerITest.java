package edu.hm.hafner.grading.github;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for the quality monitor action. Starts the container and checks if the action runs as expected.
 *
 * @author Ullrich Hafner
 */
public class QualityMonitorDockerITest {
    private static final String CONFIGURATION = """
                {
                 "tests": {
                   "name": "JUnit",
                   "tools": [
                     {
                       "id": "junit",
                       "name": "Unittests",
                       "pattern": "**/target/*-reports/TEST*.xml"
                     }
                   ]
                 },
                 "analysis": [
                   {
                     "name": "Style",
                     "id": "style",
                     "tools": [
                       {
                         "id": "checkstyle",
                         "name": "CheckStyle",
                         "pattern": "**/checkstyle*.xml"
                       },
                       {
                         "id": "pmd",
                         "name": "PMD",
                         "pattern": "**/pmd*.xml"
                       }
                     ]
                   },
                   {
                     "name": "Bugs",
                     "id": "bugs",
                     "tools": [
                       {
                         "id": "spotbugs",
                         "name": "SpotBugs",
                         "pattern": "**/spotbugs*.xml"
                       }
                     ]
                   }
                 ],
                 "coverage": [
                   {
                     "name": "JaCoCo",
                     "tools": [
                       {
                         "id": "jacoco",
                         "name": "Line Coverage",
                         "metric": "line",
                         "pattern": "**/jacoco.xml"
                       },
                       {
                         "id": "jacoco",
                         "name": "Branch Coverage",
                         "metric": "branch",
                         "pattern": "**/jacoco.xml"
                       }
                     ]
                   },
                   {
                     "name": "PIT",
                     "tools": [
                       {
                         "id": "pit",
                         "name": "Mutation Coverage",
                         "metric": "mutation",
                         "pattern": "**/mutations.xml"
                       }
                     ]
                   }
                 ],
                 "metrics": [
                   {
                     "name": "Toplevel Metrics",
                     "tools": [
                       {
                         "name": "Cyclomatic Complexity",
                         "id": "metrics",
                         "pattern": "**/metrics.xml",
                         "metric": "CyclomaticComplexity"
                       },
                       {
                         "name": "Cognitive Complexity",
                         "id": "metrics",
                         "pattern": "**/metrics.xml",
                         "metric": "CognitiveComplexity"
                       },
                       {
                         "name": "Non Commenting Source Statements",
                         "id": "metrics",
                         "pattern": "**/metrics.xml",
                         "metric": "NCSS"
                       },
                       {
                         "name": "N-Path Complexity",
                         "id": "metrics",
                         "pattern": "**/metrics.xml",
                         "metric": "NPathComplexity"
                       }
                     ]
                   }
                 ]
               }
             }
            """;
    private static final String WS = "/github/workspace/target/";
    private static final String LOCAL_METRICS_FILE = "target/metrics.env";

    @Test
    void shouldGradeInDockerContainer() throws TimeoutException, IOException {
        try (var container = createContainer()) {
            container.withEnv("CONFIG", CONFIGURATION);
            startContainerWithAllFiles(container);

            var metrics = new String[] {
                    "tests=1",
                    "line=10.93",
                    "branch=9.52",
                    "mutation=7.86",
                    "bugs=1",
                    "spotbugs=1",
                    "style=2",
                    "pmd=1",
                    "checkstyle=1",
                    "ncss=1200",
                    "npath-complexity=432",
                    "cognitive-complexity=172",
                    "cyclomatic-complexity=355",
                    "test-success-rate=100.00"
            };

            assertThat(readStandardOut(container))
                    .contains("Obtaining configuration from environment variable CONFIG")
                    .contains(metrics)
                    .contains("Processing 1 test configuration(s)",
                            "-> Unittests Total: 1",
                            "=> Unittests: 100.00% successful (1 passed) [Whole Project]",
                            "=> JUnit: 100.00% successful (1 passed) [Whole Project]",
                            "Processing 2 coverage configuration(s)",
                            "-> Line Coverage Total: LINE: 10.93% (33/302) [Whole Project]",
                            "=> Line Coverage: 10.93% (269 missed lines) [Whole Project]",
                            "-> Branch Coverage Total: BRANCH: 9.52% (4/42) [Whole Project]",
                            "=> Branch Coverage: 9.52% (38 missed branches) [Whole Project]",
                            "=> JaCoCo: 10.76% (307 missed items) [Whole Project]",
                            "-> Mutation Coverage Total: MUTATION: 7.86% (11/140) [Whole Project]",
                            "=> Mutation Coverage: 7.86% (129 survived mutations) [Whole Project]",
                            "=> PIT: 7.86% (129 survived mutations) [Whole Project]",
                            "Processing 2 static analysis configuration(s)",
                            "-> CheckStyle (checkstyle): 1 warning (normal: 1) [Whole Project]",
                            "-> PMD (pmd): 1 warning (normal: 1) [Whole Project]",
                            "=> Style: 2 warnings (normal: 2) [Whole Project]",
                            "-> SpotBugs (spotbugs): 1 bug (low: 1) [Whole Project]",
                            "=> Bugs: 1 bug (low: 1) [Whole Project]",
                            "=> Cyclomatic Complexity: 355 (total) [Whole Project]",
                            "=> Cognitive Complexity: 172 (total) [Whole Project]",
                            "=> Non Commenting Source Statements: 1200 (total) [Whole Project]",
                            "=> N-Path Complexity: 432 (total) [Whole Project]");

            container.copyFileFromContainer("/github/workspace/metrics.env", LOCAL_METRICS_FILE);
            assertThat(Files.readString(Path.of(LOCAL_METRICS_FILE)))
                    .contains(metrics);
        }
    }

    @Test
    void shouldUseDefaultConfiguration() throws TimeoutException {
        try (var container = createContainer()) {
            startContainerWithAllFiles(container);

            assertThat(readStandardOut(container))
                    .contains(
                            "No configuration provided (environment variable CONFIG not set), using default configuration")
                    .contains("Processing 1 test configuration(s)",
                            "-> JUnit Tests Total: 1 [Whole Project]",
                            "=> Tests: 100.00% successful (1 passed) [Whole Project]",
                            "Processing 2 coverage configuration(s)",
                            "-> Line Coverage Total: LINE: 10.93% (33/302) [Whole Project]",
                            "-> Branch Coverage Total: BRANCH: 9.52% (4/42) [Whole Project]",
                            "=> Code Coverage: 10.76% (307 missed items) [Whole Project]",
                            "-> Mutation Coverage Total: MUTATION: 7.86% (11/140) [Whole Project]",
                            "=> Mutation Coverage: 7.86% (129 survived mutations) [Whole Project]",
                            "Processing 2 static analysis configuration(s)",
                            "-> CheckStyle (checkstyle): 1 warning (normal: 1) [Whole Project]",
                            "-> PMD (pmd): 1 warning (normal: 1) [Whole Project]",
                            "=> Style: 2 warnings (normal: 2) [Whole Project]",
                            "-> SpotBugs (spotbugs): 1 bug (low: 1) [Whole Project]",
                            "=> Bugs: 1 bug (low: 1) [Whole Project]");
        }
    }

    @Test
    void shouldShowErrors() throws TimeoutException {
        try (var container = createContainer()) {
            container.withWorkingDirectory("/github/workspace").start();
            assertThat(readStandardOut(container))
                    .contains("Processing 1 test configuration(s)",
                            "=> JUnit Tests: No test results available",
                            "=> Tests: No test results available",
                            "Configuration error for 'JUnit Tests'?",
                            "Processing 2 coverage configuration(s)",
                            "Configuration error for 'Line Coverage'?",
                            "Configuration error for 'Branch Coverage'?",
                            "Configuration error for 'Mutation Coverage'?",
                            "=> Code Coverage: n/a (0 missed items)",
                            "=> Mutation Coverage: n/a (0 survived mutations)",
                            "=> Test Strength: n/a (0 survived mutations in tested code)",
                            "Processing 2 static analysis configuration(s)",
                            "Configuration error for 'CheckStyle'?",
                            "Configuration error for 'PMD'?",
                            "Configuration error for 'SpotBugs'?",
                            "-> CheckStyle (checkstyle): No warnings",
                            "-> PMD (pmd): No warnings",
                            "=> Style: No warnings",
                            "-> SpotBugs (spotbugs): No warnings",
                            "=> Bugs: No warnings");
        }
    }

    private GenericContainer<?> createContainer() {
        return new GenericContainer<>(DockerImageName.parse("uhafner/quality-monitor:4.0.0"));
    }

    private String readStandardOut(final GenericContainer<? extends GenericContainer<?>> container)
            throws TimeoutException {
        var waitingConsumer = new WaitingConsumer();
        var toStringConsumer = new ToStringConsumer();

        var composedConsumer = toStringConsumer.andThen(waitingConsumer);
        container.followOutput(composedConsumer);
        waitingConsumer.waitUntil(frame -> frame.getUtf8String().contains("End " + QualityMonitor.QUALITY_MONITOR), 60,
                TimeUnit.SECONDS);

        return toStringConsumer.toUtf8String();
    }

    private void startContainerWithAllFiles(final GenericContainer<?> container) {
        container.withWorkingDirectory("/github/workspace")
                .withCopyFileToContainer(read("checkstyle/checkstyle.xml"), WS + "checkstyle-result.xml")
                .withCopyFileToContainer(read("jacoco/jacoco.xml"), WS + "site/jacoco/jacoco.xml")
                .withCopyFileToContainer(read("junit/TEST-edu.hm.hafner.grading.AutoGradingActionTest.xml"),
                        WS + "surefire-reports/TEST-Aufgabe3Test.xml")
                .withCopyFileToContainer(read("pit/mutations.xml"), WS + "pit-reports/mutations.xml")
                .withCopyFileToContainer(read("pmd/pmd.xml"), WS + "pmd-java/pmd.xml")
                .withCopyFileToContainer(read("spotbugs/spotbugsXml.xml"), WS + "spotbugsXml.xml")
                .withCopyFileToContainer(read("metrics/metrics.xml"), WS + "metrics.xml")
                .start();
    }

    private MountableFile read(final String resourceName) {
        return MountableFile.forClasspathResource("/" + resourceName);
    }
}
