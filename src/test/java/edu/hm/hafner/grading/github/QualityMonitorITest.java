package edu.hm.hafner.grading.github;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import edu.hm.hafner.util.ResourceTest;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for the grading action.  Runs the action locally in the filesystem.
 *
 * @author Ullrich Hafner
 */
public class QualityMonitorITest extends ResourceTest {
    private static final String CONFIGURATION = """
            {
              "tests": {
                "name": "JUnit",
                "tools": [
                  {
                    "id": "test",
                    "name": "Unittests",
                    "pattern": "**/src/**/TEST*.xml"
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
                      "pattern": "**/src/**/checkstyle*.xml"
                    },
                    {
                      "id": "pmd",
                      "name": "PMD",
                      "pattern": "**/src/**/pmd*.xml"
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
                      "pattern": "**/src/**/spotbugs*.xml"
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
                        "pattern": "**/src/**/jacoco.xml"
                      },
                      {
                        "id": "jacoco",
                        "name": "Branch Coverage",
                        "metric": "branch",
                        "pattern": "**/src/**/jacoco.xml"
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
                        "pattern": "**/src/**/mutations.xml"
                      }
                    ]
              }
              ]
            }
            """;

    @Test
    void shouldMonitorQualityWithDefaultConfiguration() {
        assertThat(runAutoGrading())
                .contains(
                        "No configuration provided (environment variable CONFIG not set), using default configuration")
                .contains(new String[] {
                        "Processing 1 test configuration(s)",
                        "\"maxScore\" : 0,",
                        "\"failureImpact\" : 0,",
                        "\"passedImpact\" : 0,",
                        "\"skippedImpact\" : 0",
                        "Processing 2 coverage configuration(s)",
                        "\"coveredPercentageImpact\" : 0,",
                        "\"missedPercentageImpact\" : 0,",
                        "=> Line Coverage: 0% (0 missed lines)",
                        "=> Branch Coverage: 0% (0 missed branches)",
                        "=> Code Coverage: 0% (0 missed items)",
                        "=> Mutation Coverage: 0% (0 survived mutations)",
                        "Processing 2 static analysis configuration(s)",
                        "\"errorImpact\" : 0,",
                        "\"highImpact\" : 0,",
                        "\"lowImpact\" : 0",
                        "\"normalImpact\" : 0,",
                        "=> Style: 2 warnings found (0 error, 0 high, 2 normal, 0 low)",
                        "=> Bugs: No warnings found"});
    }

    @Test
    @SetEnvironmentVariable(key = "CONFIG", value = CONFIGURATION)
    void shouldGradeWithConfigurationFromEnvironment() {
        assertThat(runAutoGrading())
                .contains("Obtaining configuration from environment variable CONFIG")
                .contains(new String[] {
                        "Processing 1 test configuration(s)",
                        "=> JUnit: 13 tests failed, 24 passed",
                        "Processing 2 coverage configuration(s)",
                        "-> Line Coverage Total: LINE: 10.93% (33/302)",
                        "-> Branch Coverage Total: BRANCH: 9.52% (4/42)",
                        "=> JaCoCo: 10%",
                        "-> Mutation Coverage Total: MUTATION: 7.86% (11/140)",
                        "=> PIT: 8%",
                        "Processing 2 static analysis configuration(s)",
                        "-> CheckStyle Total: 19 warnings",
                        "=> CheckStyle: 19 warnings found (0 error, 0 high, 19 normal, 0 low)",
                        "-> PMD Total: 41 warnings",
                        "=> PMD: 41 warnings found (0 error, 0 high, 41 normal, 0 low)",
                        "-> SpotBugs Total: 1 warnings",
                        "=> SpotBugs: 1 warning found (0 error, 0 high, 0 normal, 1 low)",
                        "mutation=8",
                        "bugs=1",
                        "tests=37",
                        "line=11",
                        "pmd=41",
                        "style=60",
                        "spotbugs=1",
                        "checkstyle=19",
                        "branch=10"});
    }

    private static final String CONFIGURATION_WRONG_PATHS = """
            {
              "tests": {
                "tools": [
                  {
                    "id": "test",
                    "name": "Unittests",
                    "pattern": "**/does-not-exist/TEST*.xml"
                  }
                ],
                "name": "JUnit",
                "passedImpact": 10,
                "skippedImpact": -1,
                "failureImpact": -5,
                "maxScore": 100
              },
              "analysis": [
                {
                  "name": "Style",
                  "id": "style",
                  "tools": [
                    {
                      "id": "checkstyle",
                      "name": "CheckStyle",
                      "pattern": "**/does-not-exist/checkstyle*.xml"
                    },
                    {
                      "id": "pmd",
                      "name": "PMD",
                      "pattern": "**/does-not-exist/pmd*.xml"
                    }
                  ],
                  "errorImpact": 1,
                  "highImpact": 2,
                  "normalImpact": 3,
                  "lowImpact": 4,
                  "maxScore": 100
                },
                {
                  "name": "Bugs",
                  "id": "bugs",
                  "tools": [
                    {
                      "id": "spotbugs",
                      "name": "SpotBugs",
                      "pattern": "**/does-not-exist/spotbugs*.xml"
                    }
                  ],
                  "errorImpact": -11,
                  "highImpact": -12,
                  "normalImpact": -13,
                  "lowImpact": -14,
                  "maxScore": 100
                }
              ],
              "coverage": [
              {
                  "tools": [
                      {
                        "id": "jacoco",
                        "name": "Line Coverage",
                        "metric": "line",
                        "pattern": "**/does-not-exist/jacoco.xml"
                      },
                      {
                        "id": "jacoco",
                        "name": "Branch Coverage",
                        "metric": "branch",
                        "pattern": "**/does-not-exist/jacoco.xml"
                      }
                    ],
                "name": "JaCoCo",
                "maxScore": 100,
                "coveredPercentageImpact": 1,
                "missedPercentageImpact": -1
              },
              {
                  "tools": [
                      {
                        "id": "pit",
                        "name": "Mutation Coverage",
                        "metric": "mutation",
                        "pattern": "**/does-not-exist/mutations.xml"
                      }
                    ],
                "name": "PIT",
                "maxScore": 100,
                "coveredPercentageImpact": 1,
                "missedPercentageImpact": -1
              }
              ]
            }
            """;

    @Test
    @SetEnvironmentVariable(key = "CONFIG", value = CONFIGURATION_WRONG_PATHS)
    void shouldShowErrors() {
        assertThat(runAutoGrading())
                .contains(new String[] {
                        "Processing 1 test configuration(s)",
                        "-> Unittests Total: TESTS: 0 tests",
                        "Configuration error for 'Unittests'?",
                        "JUnit Score: 100 of 100",
                        "Processing 2 coverage configuration(s)",
                        "=> JaCoCo Score: 0 of 100",
                        "Configuration error for 'Line Coverage'?",
                        "Configuration error for 'Branch Coverage'?",
                        "=> PIT Score: 0 of 100",
                        "Configuration error for 'Mutation Coverage'?",
                        "Processing 2 static analysis configuration(s)",
                        "Configuration error for 'CheckStyle'?",
                        "Configuration error for 'PMD'?",
                        "Configuration error for 'SpotBugs'?",
                        "-> CheckStyle Total: 0 warnings",
                        "-> PMD Total: 0 warnings",
                        "=> Style Score: 0 of 100",
                        "-> SpotBugs Total: 0 warnings",
                        "=> Bugs Score: 100 of 100",
                        "Autograding score - 200 of 500"});
    }

    private String runAutoGrading() {
        var outputStream = new ByteArrayOutputStream();
        var runner = new QualityMonitor(new PrintStream(outputStream));
        runner.run();
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
