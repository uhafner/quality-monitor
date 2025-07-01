package edu.hm.hafner.grading.github;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.grading.AggregatedScore;
import edu.hm.hafner.grading.AutoGradingRunner;
import edu.hm.hafner.grading.GradingReport;
import edu.hm.hafner.grading.QualityGate;
import edu.hm.hafner.grading.QualityGateResult;
import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHCheckRunBuilder.Output;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GitHub action entrypoint for the quality monitor action.
 *
 * @author Ullrich Hafner
 */
public class QualityMonitor extends AutoGradingRunner {
    static final String QUALITY_MONITOR = "Quality Monitor";

    /**
         * Public entry point for the GitHub action in the docker container, simply calls the action.
         *
         * @param unused
         *         not used
         */
    public static void main(final String... unused) {
        new QualityMonitor().run();
    }

    /**
     * Creates a new instance of {@link QualityMonitor}.
     */
    public QualityMonitor() {
        super();
    }

    @VisibleForTesting
    QualityMonitor(final PrintStream printStream) {
        super(printStream);
    }

    @Override
    protected String getDisplayName() {
        return QUALITY_MONITOR;
    }

    @Override
    protected String getDefaultConfigurationPath() {
        return "/default-no-score-config.json";
    }

    @Override
    protected void publishGradingResult(final AggregatedScore score, final FilteredLog log) {
        var errors = createErrorMessageMarkdown(log);

        var results = new GradingReport();

        // Parse and evaluate quality gates
        var qualityGates = parseQualityGates(log);
        var qualityGateResult = evaluateQualityGates(score, qualityGates, log);
        
        // Determine conclusion based on quality gates and errors
        var conclusion = determineConclusion(errors, qualityGateResult, log);
        
        // Add quality gate details to the output
        var qualityGateDetails = createQualityGateMarkdown(qualityGateResult, log);

        var showHeaders = StringUtils.isNotBlank(getEnv("SHOW_HEADERS", log));
        addComment(score,
                results.getTextSummary(score, getChecksName()),
                results.getMarkdownDetails(score, getChecksName()) + errors + qualityGateDetails,
                results.getSubScoreDetails(score) + errors + qualityGateDetails,
                results.getMarkdownSummary(score, getChecksName(), showHeaders) + errors + qualityGateDetails,
                conclusion, log);

        try {
            var environmentVariables = createEnvironmentVariables(score, log);
            Files.writeString(Paths.get("metrics.env"), environmentVariables);
        }
        catch (IOException exception) {
            log.logException(exception, "Can't write environment variables to 'metrics.env'");
        }

        log.logInfo("GitHub Action has finished");
    }

    @Override
    protected void publishError(final AggregatedScore score, final FilteredLog log, final Throwable exception) {
        var results = new GradingReport();

        var markdownErrors = results.getMarkdownErrors(score, exception);
        addComment(score, results.getTextSummary(score, getChecksName()),
                markdownErrors, markdownErrors, markdownErrors, Conclusion.FAILURE, log);
    }

    private void addComment(final AggregatedScore score, final String textSummary,
            final String markdownDetails, final String markdownSummary, final String prSummary,
            final Conclusion conclusion, final FilteredLog log) {
        try {
            var repository = getEnv("GITHUB_REPOSITORY", log);
            if (repository.isBlank()) {
                log.logError("No GITHUB_REPOSITORY defined - skipping");

                return;
            }

            String oAuthToken = getEnv("GITHUB_TOKEN", log);
            if (oAuthToken.isBlank()) {
                log.logError("No valid GITHUB_TOKEN found - skipping");
                return;
            }

            String apiUrl = getEnv("GITHUB_API_URL", log);

            String sha = getEnv("GITHUB_SHA", log);

            GitHubBuilder githubBuilder = new GitHubBuilder()
                    .withAppInstallationToken(oAuthToken);
            if (!apiUrl.isBlank()) {
                githubBuilder.withEndpoint(apiUrl);
            }
            GitHub github = githubBuilder.build();

            GHCheckRunBuilder check = github.getRepository(repository)
                    .createCheckRun(getChecksName(), sha)
                    .withStatus(Status.COMPLETED)
                    .withStartedAt(Date.from(Instant.now()))
                    .withConclusion(conclusion);

            var summaryWithFooter = markdownSummary + "\n\nCreated by " + getVersionLink(log);
            Output output = new Output(textSummary, summaryWithFooter).withText(markdownDetails);

            if (getEnv("SKIP_ANNOTATIONS", log).isEmpty()) {
                var annotationBuilder = new GitHubAnnotationsBuilder(
                        output, computeAbsolutePathPrefixToRemove(log), log);
                annotationBuilder.createAnnotations(score);
            }

            check.add(output);

            var checksResult = createChecksRun(log, check);

            var prNumber = getEnv("PR_NUMBER", log);
            if (!prNumber.isBlank()) { // optional PR comment
                var footer = "Created by %s. %s".formatted(getVersionLink(log), checksResult);
                github.getRepository(repository)
                        .getPullRequest(Integer.parseInt(prNumber))
                        .comment(prSummary + "\n\n" + footer + "\n");
                log.logInfo("Successfully commented PR#" + prNumber);
            }
        }
        catch (IOException exception) {
            logException(log, exception, "Could create GitHub comments");
        }
    }

    private String createChecksRun(final FilteredLog log, final GHCheckRunBuilder check) {
        try {
            GHCheckRun run = check.create();
            log.logInfo("Successfully created check " + run);

            return "More details are shown in the [GitHub Checks Result](%s).".formatted(run.getDetailsUrl().toString());
        }
        catch (IOException exception) {
            logException(log, exception, "Could not create check");

            return "A detailed GitHub Checks Result could not be created, see error log.";
        }
    }

    private void logException(final FilteredLog log, final IOException exception, final String message) {
        String errorMessage;
        if (exception instanceof HttpException responseException) {
            errorMessage = StringUtils.defaultIfBlank(responseException.getResponseMessage(), exception.getMessage());
        }
        else {
            errorMessage = exception.getMessage();
        }
        log.logError("%s: %s", message, StringUtils.defaultIfBlank(errorMessage, "no error message available"));
    }

    private String getVersionLink(final FilteredLog log) {
        var version = readVersion(log);
        var sha = readSha(log);
        return "[%s](https://github.com/uhafner/quality-monitor/releases/tag/v%s) v%s (#%s)"
                .formatted(getDisplayName(), version, version, sha);
    }

    String createEnvironmentVariables(final AggregatedScore score, final FilteredLog log) {
        var metrics = new StringBuilder();
        score.getMetrics().forEach((metric, value) ->
                metrics.append(String.format(Locale.ENGLISH, "%s=%d%n", metric, value)));
        log.logInfo("---------------");
        log.logInfo("Metrics Summary");
        log.logInfo("---------------");
        log.logInfo(metrics.toString());
        return metrics.toString();
    }

    private String getChecksName() {
        return StringUtils.defaultIfBlank(System.getenv("CHECKS_NAME"), getDisplayName());
    }

    private String computeAbsolutePathPrefixToRemove(final FilteredLog log) {
        return String.format("%s/%s/", getEnv("RUNNER_WORKSPACE", log),
                StringUtils.substringAfter(getEnv("GITHUB_REPOSITORY", log), "/"));
    }

    private String getEnv(final String key, final FilteredLog log) {
        String value = StringUtils.defaultString(System.getenv(key));
        log.logInfo(">>>> " + key + ": " + value);
        return value;
    }

    /**
     * Parses quality gates from the QUALITY_GATES environment variable.
     *
     * @param log the logger
     * @return the list of quality gates
     */
    private List<QualityGate> parseQualityGates(final FilteredLog log) {
        var qualityGatesJson = getEnv("QUALITY_GATES", log);
        if (StringUtils.isBlank(qualityGatesJson)) {
            log.logInfo("No quality gates configured (QUALITY_GATES environment variable not set)");
            return new ArrayList<>();
        }

        try {
            log.logInfo("Parsing quality gates from QUALITY_GATES environment variable");
            return parseQualityGatesFromJson(qualityGatesJson, log);
        }
        catch (Exception exception) {
            log.logError("Failed to parse quality gates: %s", exception.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parses quality gates from JSON string.
     *
     * @param json the JSON string
     * @param log the logger
     * @return the list of quality gates
     */
    private List<QualityGate> parseQualityGatesFromJson(final String json, final FilteredLog log) 
            throws JsonProcessingException {
        var mapper = new ObjectMapper();
        var jsonNode = mapper.readTree(json);
        var qualityGates = new ArrayList<QualityGate>();

        if (jsonNode.isArray()) {
            for (var gateNode : jsonNode) {
                var gate = parseQualityGate(gateNode, log);
                if (gate != null) {
                    qualityGates.add(gate);
                }
            }
        }
        else {
            log.logError("Quality gates JSON must be an array");
        }

        log.logInfo("Parsed %d quality gate(s)", qualityGates.size());
        return qualityGates;
    }

    /**
     * Parses a single quality gate from JSON node.
     *
     * @param gateNode the JSON node
     * @param log the logger
     * @return the quality gate or null if parsing failed
     */
    private QualityGate parseQualityGate(final JsonNode gateNode, final FilteredLog log) {
        try {
            var name = gateNode.path("name").asText("");
            var metric = gateNode.path("metric").asText("");
            var threshold = gateNode.path("threshold").asDouble(0.0);
            var operatorStr = gateNode.path("operator").asText("GREATER_THAN_OR_EQUAL");
            var criticalityStr = gateNode.path("criticality").asText("UNSTABLE");
            var enabled = gateNode.path("enabled").asBoolean(true);

            if (StringUtils.isBlank(name) || StringUtils.isBlank(metric)) {
                log.logError("Quality gate missing required fields: name=%s, metric=%s", name, metric);
                return null;
            }

            var operator = parseOperator(operatorStr, log);
            var criticality = parseCriticality(criticalityStr, log);

            var gate = new QualityGate(name, metric, threshold, operator, criticality, enabled);
            log.logInfo("Parsed quality gate: %s", gate);
            return gate;
        }
        catch (Exception exception) {
            log.logError("Failed to parse quality gate: %s", exception.getMessage());
            return null;
        }
    }

    /**
     * Parses operator from string.
     */
    private QualityGate.Operator parseOperator(final String operatorStr, final FilteredLog log) {
        try {
            return QualityGate.Operator.valueOf(operatorStr.toUpperCase());
        }
        catch (IllegalArgumentException exception) {
            log.logError("Unknown operator '%s', using GREATER_THAN_OR_EQUAL", operatorStr);
            return QualityGate.Operator.GREATER_THAN_OR_EQUAL;
        }
    }

    /**
     * Parses criticality from string.
     */
    private QualityGate.Criticality parseCriticality(final String criticalityStr, final FilteredLog log) {
        try {
            return QualityGate.Criticality.valueOf(criticalityStr.toUpperCase());
        }
        catch (IllegalArgumentException exception) {
            log.logError("Unknown criticality '%s', using UNSTABLE", criticalityStr);
            return QualityGate.Criticality.UNSTABLE;
        }
    }

    /**
     * Evaluates quality gates against the aggregated score.
     *
     * @param score the aggregated score
     * @param qualityGates the quality gates to evaluate
     * @param log the logger
     * @return the evaluation result
     */
    private QualityGateResult evaluateQualityGates(final AggregatedScore score, 
            final List<QualityGate> qualityGates, final FilteredLog log) {
        if (qualityGates.isEmpty()) {
            return new QualityGateResult();
        }

        log.logInfo("Evaluating %d quality gate(s)", qualityGates.size());
        var result = score.evaluateQualityGates(qualityGates);
        
        log.logInfo("Quality gates evaluation completed: %s", result.getOverallStatus());
        log.logInfo("  Passed: %d, Failed: %d", result.getSuccessCount(), result.getFailureCount());
        
        for (var evaluation : result.getEvaluations()) {
            if (evaluation.isPassed()) {
                log.logInfo("  ✅ %s", evaluation.getMessage());
            } else {
                log.logError("  ❌ %s", evaluation.getMessage());
            }
        }
        
        return result;
    }

    /**
     * Determines the GitHub check conclusion based on errors and quality gate results.
     *
     * @param errors the error messages
     * @param qualityGateResult the quality gate evaluation result
     * @param log the logger
     * @return the conclusion
     */
    private Conclusion determineConclusion(final String errors, final QualityGateResult qualityGateResult, 
            final FilteredLog log) {
        if (!errors.isBlank()) {
            log.logInfo("Setting conclusion to FAILURE due to errors");
            return Conclusion.FAILURE;
        }

        var status = qualityGateResult.getOverallStatus();
        switch (status) {
            case FAILURE:
                log.logInfo("Setting conclusion to FAILURE due to quality gate failures");
                return Conclusion.FAILURE;
            case UNSTABLE:
                log.logInfo("Setting conclusion to NEUTRAL due to quality gate warnings");
                return Conclusion.NEUTRAL;
            default:
                log.logInfo("Setting conclusion to SUCCESS - all quality gates passed");
                return Conclusion.SUCCESS;
        }
    }

    /**
     * Creates markdown content for quality gate results.
     *
     * @param result the quality gate result
     * @param log the logger
     * @return the markdown content
     */
    private String createQualityGateMarkdown(final QualityGateResult result, final FilteredLog log) {
        if (result.getEvaluations().isEmpty()) {
            return "";
        }

        var markdown = new StringBuilder();
        markdown.append("\n\n## 🚦 Quality Gates\n\n");
        
        var status = result.getOverallStatus();
        String statusIcon;
        switch (status) {
            case SUCCESS:
                statusIcon = "✅";
                break;
            case UNSTABLE:
                statusIcon = "⚠️";
                break;
            case FAILURE:
                statusIcon = "❌";
                break;
            default:
                statusIcon = "❓";
                break;
        }
        
        markdown.append(String.format("**Overall Status:** %s %s\n\n", statusIcon, status));
        markdown.append(String.format("**Summary:** %d total, %d passed, %d failed\n\n", 
                result.getEvaluations().size(), result.getSuccessCount(), result.getFailureCount()));

        if (result.getFailureCount() > 0) {
            markdown.append("### Failed Gates\n\n");
            for (var evaluation : result.getFailedEvaluations()) {
                markdown.append(String.format("- %s\n", evaluation.getMessage()));
            }
            markdown.append("\n");
        }

        if (result.getSuccessCount() > 0) {
            markdown.append("### Passed Gates\n\n");
            for (var evaluation : result.getSuccessfulEvaluations()) {
                markdown.append(String.format("- %s\n", evaluation.getMessage()));
            }
        }

        return markdown.toString();
    }
}
