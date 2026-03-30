package edu.hm.hafner.grading.github;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.grading.CommentBuilder;
import edu.hm.hafner.util.FilteredLog;

import java.util.Map;
import java.util.Set;

import org.kohsuke.github.GHCheckRun.AnnotationLevel;
import org.kohsuke.github.GHCheckRunBuilder.Annotation;
import org.kohsuke.github.GHCheckRunBuilder.Output;

/**
 * Creates GitHub annotations for static analysis warnings, for lines with missing coverage, and for lines with
 * survived mutations.
 *
 * @author Ullrich Hafner
 */
class GitHubAnnotationsBuilder extends CommentBuilder {
    private static final String GITHUB_WORKSPACE_REL = "/github/workspace/./";
    private static final String GITHUB_WORKSPACE_ABS = "/github/workspace/";

    private final Output output;
    private final FilteredLog log;
    private final int maxWarningComments;
    private final int maxCoverageComments;
    private final boolean isLoggingEnabled;

    GitHubAnnotationsBuilder(final Map<String, Set<Integer>> modifiedFilesAndLines,
            final Output output, final String prefix, final FilteredLog log) {
        super(modifiedFilesAndLines, prefix, GITHUB_WORKSPACE_REL, GITHUB_WORKSPACE_ABS);

        this.output = output;
        this.log = log;

        maxWarningComments = getIntegerEnvironmentWithDefault("MAX_WARNING_ANNOTATIONS");
        maxCoverageComments = getIntegerEnvironmentWithDefault("MAX_COVERAGE_ANNOTATIONS");

        isLoggingEnabled = StringUtils.isNotBlank(getEnv("LOG_COMMENTS"));
    }

    @Override
    protected final int getMaxWarningComments() {
        return maxWarningComments;
    }

    @Override
    protected final int getMaxCoverageComments() {
        return maxCoverageComments;
    }

    private int getIntegerEnvironmentWithDefault(final String key) {
        var value = getEnv(key);
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException _) {
            if (StringUtils.isEmpty(value)) {
                log.logInfo(">>>> Environment variable %s not set, falling back to default Integer.MAX_VALUE", key);
            }
            else {
                log.logError(">>>> Error: no integer value in environment variable key %s: %s, falling back to default Integer.MAX_VALUE", key, value);
            }

            return Integer.MAX_VALUE;
        }
    }

    private String getEnv(final String name) {
        return StringUtils.defaultString(System.getenv(name));
    }

    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    protected boolean createComment(final CommentType commentType, final String relativePath,
            final int lineStart, final int lineEnd,
            final String message, final String title,
            final int columnStart, final int columnEnd,
            final String details, final String markDownDetails) {
        if (isLoggingEnabled) {
            log.logInfo("Creating annotation for %s in %s", relativePath, GITHUB_WORKSPACE_REL);
            log.logInfo("Line start is %d, line end is %d", lineStart, lineEnd);
            log.logInfo("CommentType is %s", commentType);
            log.logInfo("Message is %s", message);
            log.logInfo("Full Message is %s", markDownDetails);
        }
        if (!isPartOfChangedFiles(relativePath, lineStart, lineEnd) && commentType != CommentType.WARNING) {
            return false; // do not create coverage comments for lines that are not part of the diff
        }

        // GitHub annotations are 1-based, so we have to adjust the line numbers if some tools annotate the whole file
        int actualLineStart;
        int actualLineEnd;
        if (lineStart == 0) {
            actualLineStart = 1;
            actualLineEnd = 1;
        }
        else {
            actualLineStart = lineStart;
            actualLineEnd = lineEnd;
        }
        var annotation = new Annotation(relativePath,
                actualLineStart, actualLineEnd, AnnotationLevel.WARNING, message).withTitle(title);

        if (lineStart == lineEnd) {
            annotation.withStartColumn(columnStart).withEndColumn(columnEnd);
        }
        if (StringUtils.isNotBlank(details)) {
            annotation.withRawDetails(details);
        }

        output.add(annotation);

        return true;
    }
}
