package edu.hm.hafner.grading.github;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import edu.hm.hafner.util.ResourceTest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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
                "tools": [
                  {
                    "id": "junit",
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
                      "pattern": "**/src/**/checkstyle*.xml"
                    },
                    {
                      "id": "pmd",
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
                        "metric": "line",
                        "pattern": "**/src/**/jacoco.xml"
                      },
                      {
                        "id": "jacoco",
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
                        "metric": "mutation",
                        "pattern": "**/src/**/mutations.xml"
                      }
                    ]
              }
              ],
              "metrics": [
                      {
                        "name": "Toplevel Metrics",
                        "tools": [
                          {
                            "id": "metrics",
                            "pattern": "**/src/**/metrics.xml",
                            "metric": "CyclomaticComplexity"
                          },
                          {
                            "id": "metrics",
                            "pattern": "**/src/**/metrics.xml",
                            "metric": "CognitiveComplexity"
                          },
                          {
                            "id": "metrics",
                            "pattern": "**/src/**/metrics.xml",
                            "metric": "NPathComplexity"
                          },
                          {
                            "id": "metrics",
                            "pattern": "**/src/**/metrics.xml",
                            "metric": "NCSS"
                          },
                          {
                            "id": "metrics",
                            "pattern": "**/src/**/metrics.xml",
                            "metric": "LOC"
                          },
                          {
                            "id": "metrics",
                            "pattern": "**/src/**/metrics.xml",
                            "metric": "COHESION"
                          },
                          {
                            "id": "metrics",
                            "pattern": "**/src/**/metrics.xml",
                            "metric": "WEIGHT_OF_CLASS"
                          }
                        ]
                      }
                    ]
            }
            """;

    private static final String CONFIGURATION_WRONG_PATHS = """
            {
              "tests": {
                "tools": [
                  {
                    "id": "junit",
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
    private static final String QUALITY_GATES_OK = """
            {
              "qualityGates": [
                {
                  "metric": "line",
                  "threshold": 10.0,
                  "criticality": "FAILURE"
                }
              ]
            }
            """;
    private static final String QUALITY_GATES_NOK = """
            {
              "qualityGates": [
                {
                  "metric": "line",
                  "threshold": 100.0,
                  "criticality": "FAILURE"
                }
              ]
            }
            """;

    @Test
    void shouldMonitorQualityWithDefaultConfiguration() {
        assertThat(runAutoGrading())
                .contains("No configuration provided (environment variable CONFIG not set), using default configuration")
                .contains("Processing 1 test configuration(s)",
                        "\"maxScore\" : 0,",
                        "\"failureImpact\" : 0,",
                        "\"passedImpact\" : 0,",
                        "\"skippedImpact\" : 0",
                        "Processing 2 coverage configuration(s)",
                        "\"coveredPercentageImpact\" : 0,",
                        "\"missedPercentageImpact\" : 0,",
                        "Processing 2 static analysis configuration(s)",
                        "\"errorImpact\" : 0,",
                        "\"highImpact\" : 0,",
                        "\"lowImpact\" : 0",
                        "\"normalImpact\" : 0,");
    }

    @Test
    @SetEnvironmentVariable(key = "CONFIG", value = CONFIGURATION)
    void shouldGradeWithConfigurationFromEnvironment() {
        assertThat(runAutoGrading())
                .contains("Obtaining configuration from environment variable CONFIG")
                .contains("Processing 1 test configuration(s)",
                        "=> Tests: 64.86% successful (13 failed, 24 passed)",
                        "Processing 2 coverage configuration(s)",
                        "-> Line Coverage Total: LINE: 10.93% (33/302) [Whole Project]",
                        "-> Branch Coverage Total: BRANCH: 9.52% (4/42) [Whole Project]",
                        "=> JaCoCo: 10.76% (307 missed items) [Whole Project]",
                        "-> Mutation Coverage Total: MUTATION: 7.86% (11/140) [Whole Project]",
                        "=> PIT: 7.86% (129 survived mutations) [Whole Project]",
                        "Processing 2 static analysis configuration(s)",
                        "-> CheckStyle (checkstyle): 19 warnings (normal: 19) [Whole Project]",
                        "=> CheckStyle: 19 warnings (normal: 19) [Whole Project]",
                        "-> PMD (pmd): 41 warnings (normal: 41) [Whole Project]",
                        "=> PMD: 41 warnings (normal: 41) [Whole Project]",
                        "-> SpotBugs (spotbugs): 1 bug (low: 1) [Whole Project]",
                        "=> SpotBugs: 1 bug (low: 1) [Whole Project]",
                        "=> Cyclomatic Complexity: 355 (total) [Whole Project]",
                        "=> Cognitive Complexity: 172 (total) [Whole Project]",
                        "=> Non Commenting Source Statements: 1200 (total) [Whole Project]",
                        "=> N-Path Complexity: 432 (total) [Whole Project]")
                .contains("Environment variable 'QUALITY_GATES' not found or empty",
                        "No quality gates to evaluate")
                .contains("loc=0",
                        "line=10.93",
                        "pmd=41",
                        "ncss=1200",
                        "npath-complexity=432",
                        "weight-of-class=0.00",
                        "branch=9.52",
                        "mutation=7.86",
                        "bugs=1",
                        "tests=37",
                        "style=60",
                        "spotbugs=1",
                        "cognitive-complexity=172",
                        "test-success-rate=64.86",
                        "cohesion=0.00",
                        "checkstyle=19",
                        "cyclomatic-complexity=355");
    }

    @Test
    @SetEnvironmentVariable(key = "CONFIG", value = CONFIGURATION)
    @SetEnvironmentVariable(key = "QUALITY_GATES", value = QUALITY_GATES_NOK)
    void shouldGradeWithFailedQualityGate() {
        assertThat(runAutoGrading())
                .contains("Processing 1 test configuration(s)",
                        "Processing 2 coverage configuration(s)",
                        "Processing 2 static analysis configuration(s)")
                .contains("Quality Gates Quality Monitor",
                        "Found quality gates configuration in environment variable 'QUALITY_GATES'",
                        "Parsed 1 quality gate(s) from JSON configuration",
                        "Quality gates evaluation completed: ❌ FAILURE",
                        "Passed: 0, Failed: 1",
                        "❌ Line Coverage (Whole Project): **10.93** >= 100.00");
    }

    @Test
    @SetEnvironmentVariable(key = "CONFIG", value = CONFIGURATION)
    @SetEnvironmentVariable(key = "QUALITY_GATES", value = QUALITY_GATES_OK)
    void shouldGradeWithSuccessfulQualityGate() {
        assertThat(runAutoGrading())
                .contains("Processing 1 test configuration(s)",
                        "Processing 2 coverage configuration(s)",
                        "Processing 2 static analysis configuration(s)")
                .contains("Found quality gates configuration in environment variable 'QUALITY_GATES'",
                        "Parsing quality gates from JSON configuration using QualityGatesConfiguration",
                        "Parsed 1 quality gate(s) from JSON configuration",
                        "Evaluating 1 quality gate(s)",
                        "Quality gates evaluation completed: ✅ SUCCESS",
                        "  Passed: 1, Failed: 0",
                        "  ✅ Line Coverage (Whole Project): **10.93** >= 10.00",
                        "Setting conclusion to SUCCESS - all quality gates passed");
    }

    @Test
    @SetEnvironmentVariable(key = "CONFIG", value = CONFIGURATION_WRONG_PATHS)
    void shouldShowErrors() {
        assertThat(runAutoGrading())
                .contains("Processing 1 test configuration(s)",
                        "=> JUnit Score: 100 of 100",
                        "Configuration error for 'Unittests'?",
                        "JUnit Score: 100 of 100",
                        "Processing 2 coverage configuration(s)",
                        "=> JaCoCo Score: 100 of 100",
                        "Configuration error for 'Line Coverage'?",
                        "Configuration error for 'Branch Coverage'?",
                        "=> PIT Score: 100 of 100",
                        "Configuration error for 'Mutation Coverage'?",
                        "Processing 2 static analysis configuration(s)",
                        "Configuration error for 'CheckStyle'?",
                        "Configuration error for 'PMD'?",
                        "Configuration error for 'SpotBugs'?",
                        "-> CheckStyle (checkstyle): No warnings",
                        "-> PMD (pmd): No warnings",
                        "=> Style Score: 0 of 100",
                        "-> SpotBugs (spotbugs): No warnings",
                        "=> Bugs Score: 100 of 100",
                        "Autograding score - 400 of 500");
    }

    private String runAutoGrading() {
        var outputStream = new ByteArrayOutputStream();
        var runner = new QualityMonitor(new PrintStream(outputStream, true, StandardCharsets.UTF_8));
        runner.run();
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
