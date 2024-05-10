package edu.hm.hafner.grading.github;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for the quality monitor action.
 * Starts the container and checks if the action runs as expected.
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
                    "id": "test",
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
              ]
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
                    "line=11",
                    "branch=10",
                    "mutation=8",
                    "bugs=1",
                    "spotbugs=1",
                    "style=2",
                    "pmd=1",
                    "checkstyle=1"};

            assertThat(readStandardOut(container))
                    .contains("Obtaining configuration from environment variable CONFIG")
                    .contains(metrics)
                    .contains(new String[] {
                            "Processing 1 test configuration(s)",
                            "-> Unittests Total: TESTS: 1 tests",
                            "=> Unittests: 1 tests passed",
                            "=> JUnit: 1 tests passed",
                            "Processing 2 coverage configuration(s)",
                            "-> Line Coverage Total: LINE: 10.93% (33/302)",
                            "=> Line Coverage: 11% (269 missed lines)",
                            "-> Branch Coverage Total: BRANCH: 9.52% (4/42)",
                            "=> Branch Coverage: 10% (38 missed branches)",
                            "=> JaCoCo: 10% (307 missed items)",
                            "-> Mutation Coverage Total: MUTATION: 7.86% (11/140)",
                            "=> PIT: 8% (129 survived mutations)",
                            "Processing 2 static analysis configuration(s)",
                            "-> CheckStyle Total: 1 warnings",
                            "-> PMD Total: 1 warnings",
                            "=> Style: 2 warnings (0 error, 0 high, 2 normal, 0 low)",
                            "-> SpotBugs Total: 1 warnings",
                            "=> Bugs: 1 warning (0 error, 0 high, 0 normal, 1 low)"});

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
                    .contains("No configuration provided (environment variable CONFIG not set), using default configuration")
                    .contains(new String[] {
                            "Processing 1 test configuration(s)",
                            "-> Tests Total: TESTS: 1 tests",
                            "=> Tests: 1 tests passed",
                            "Processing 2 coverage configuration(s)",
                            "-> Line Coverage Total: LINE: 10.93% (33/302)",
                            "-> Branch Coverage Total: BRANCH: 9.52% (4/42)",
                            "=> Code Coverage: 10%",
                            "-> Mutation Coverage Total: MUTATION: 7.86% (11/140)",
                            "=> Mutation Coverage: 8% ",
                            "Processing 2 static analysis configuration(s)",
                            "-> CheckStyle Total: 1 warnings",
                            "-> PMD Total: 1 warnings",
                            "=> Style: 2 warnings (0 error, 0 high, 2 normal, 0 low)",
                            "-> SpotBugs Total: 1 warnings",
                            "=> Bugs: 1 warning (0 error, 0 high, 0 normal, 1 low)"});
        }
    }

    @Test
    void shouldShowErrors() throws TimeoutException {
        try (var container = createContainer()) {
            container.withWorkingDirectory("/github/workspace").start();
            assertThat(readStandardOut(container))
                    .contains(new String[] {
                            "Processing 1 test configuration(s)",
                            "-> Tests Total: TESTS: 0 tests",
                            "Configuration error for 'Tests'?",
                            "=> Tests: 0 tests passed",
                            "Processing 2 coverage configuration(s)",
                            "=> Code Coverage: 0%",
                            "Configuration error for 'Line Coverage'?",
                            "Configuration error for 'Branch Coverage'?",
                            "=> Mutation Coverage: 0%",
                            "Configuration error for 'Mutation Coverage'?",
                            "Processing 2 static analysis configuration(s)",
                            "Configuration error for 'CheckStyle'?",
                            "Configuration error for 'PMD'?",
                            "Configuration error for 'SpotBugs'?",
                            "-> CheckStyle Total: 0 warnings",
                            "-> PMD Total: 0 warnings",
                            "=> Style: No warnings",
                            "-> SpotBugs Total: 0 warnings",
                            "=> Bugs: No warnings"});
        }
    }

    private GenericContainer<?> createContainer() {
        return new GenericContainer<>(DockerImageName.parse("uhafner/quality-monitor:1.9.0-SNAPSHOT"));
    }

    private String readStandardOut(final GenericContainer<? extends GenericContainer<?>> container) throws TimeoutException {
        var waitingConsumer = new WaitingConsumer();
        var toStringConsumer = new ToStringConsumer();

        var composedConsumer = toStringConsumer.andThen(waitingConsumer);
        container.followOutput(composedConsumer);
        waitingConsumer.waitUntil(frame -> frame.getUtf8String().contains("End " + QualityMonitor.QUALITY_MONITOR), 60, TimeUnit.SECONDS);

        return toStringConsumer.toUtf8String();
    }

    private void startContainerWithAllFiles(final GenericContainer<?> container) {
        container.withWorkingDirectory("/github/workspace")
                .withCopyFileToContainer(read("checkstyle/checkstyle-result.xml"), WS + "checkstyle-result.xml")
                .withCopyFileToContainer(read("jacoco/jacoco.xml"), WS + "site/jacoco/jacoco.xml")
                .withCopyFileToContainer(read("junit/TEST-edu.hm.hafner.grading.AutoGradingActionTest.xml"), WS + "surefire-reports/TEST-Aufgabe3Test.xml")
                .withCopyFileToContainer(read("pit/mutations.xml"), WS + "pit-reports/mutations.xml")
                .withCopyFileToContainer(read("pmd/pmd.xml"), WS + "pmd.xml")
                .withCopyFileToContainer(read("spotbugs/spotbugsXml.xml"), WS + "spotbugsXml.xml")
                .start();
    }

    private MountableFile read(final String resourceName) {
        return MountableFile.forClasspathResource("/" + resourceName);
    }
}
