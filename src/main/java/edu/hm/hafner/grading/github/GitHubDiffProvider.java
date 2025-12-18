package edu.hm.hafner.grading.github;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

/**
 * Provides changed lines for a GitHub pull request so patch coverage can be computed.
 *
 * <p>
 * Calls the GitHub REST API to list PR files and parses unified diffs from each file's {@code patch} field into
 * per-file 1-based line numbers for the new file (added or replaced lines only). Renamed files are keyed by the new
 * filename.
 * </p>
 */
class GitHubDiffProvider {
    private static final String DELTA_ANALYSIS_PREFIX = "[Delta Analysis] ";
    private static final Pattern HUNK_REGEXP = Pattern.compile(
            "^@@ -(?<oldStart>\\d+)(?:,\\d+)? \\+(?<newStart>\\d+)(?:,\\d+)? @@.*$");
    private static final String DIFF_REMOVED = "removed";
    private static final String DIFF_RENAMED = "renamed";
    private static final String DIFF_COPIED = "copied";

    /**
     * Loads changed lines per file from a GitHub PR.
     *
     * @param repository
     *         the {@code owner/repo}
     * @param token
     *         the GitHub token
     * @param apiUrl
     *         optional alternative API URL
     * @param log
     *         logger
     * @param prNumber
     *         the pull request number
     *
     * @return a mapping of a repository-relative file path to a set of 1-based changed line numbers
     */
    Map<String, Set<Integer>> loadChangedLines(final String repository,
            final String token, final String apiUrl, final FilteredLog log, final int prNumber) {
        try {
            Map<String, Set<Integer>> changedLinesByPath = new HashMap<>();

            var github = connectWithGitHub(token, apiUrl);
            var files = github.getRepository(repository).getPullRequest(prNumber).listFiles();
            for (GHPullRequestFileDetail file : files) {
                String status = safeLower(file.getStatus());
                String newPath = normalize(file.getFilename()); // use new filename for renames
                String oldPath = normalize(file.getPreviousFilename());

                if (DIFF_REMOVED.equals(status)) {
                    // Skip removed files; only added/modified/renamed/copied are relevant
                    continue;
                }

                String patch = file.getPatch();
                if (StringUtils.isBlank(patch)) { // no patch available
                    log.logInfo(createEmptyPatchMessage(newPath, oldPath, status));

                    continue;
                }

                log.logInfo(getProcessingMessage(status, newPath, oldPath));

                Set<Integer> lines = parseUnifiedDiffForNewFileAddedLines(patch);
                if (!lines.isEmpty()) {
                    changedLinesByPath.put(newPath, lines);
                }
            }

            log.logInfo(DELTA_ANALYSIS_PREFIX
                    + "Loaded changed lines for %d file(s) from PR#%d", changedLinesByPath.size(), prNumber);
            return changedLinesByPath;
        }
        catch (IOException exception) {
            log.logException(exception, DELTA_ANALYSIS_PREFIX + "Failed to load changed lines from GitHub");

            return Map.of();
        }
    }

    private String getProcessingMessage(final String status, final String newPath, final String oldPath) {
        var logMessage = (DELTA_ANALYSIS_PREFIX + "Processing %s file: %s").formatted(status, newPath);
        if (!oldPath.isBlank() && Strings.CI.equalsAny(status, DIFF_RENAMED, DIFF_COPIED)) {
            logMessage += " (previous=%s)".formatted(oldPath);
        }
        return logMessage;
    }

    private String createEmptyPatchMessage(final String newPath, final String oldPath, final String status) {
        var message = (DELTA_ANALYSIS_PREFIX + "Skipping file without patch (possibly binary/large): %s ").formatted(
                newPath);
        if (oldPath.isBlank()) {
            return message + "(status=%s)".formatted(status);
        }
        else {
            return message + "(status=%s, previous=%s)".formatted(status, oldPath);
        }
    }

    private GitHub connectWithGitHub(final String token, final String apiUrl) throws IOException {
        GitHubBuilder builder = new GitHubBuilder().withOAuthToken(token);
        if (!isBlank(apiUrl)) {
            builder.withEndpoint(apiUrl);
        }
        return builder.build();
    }

    /**
     * Parses a unified diff text for one file and returns the 1-based line numbers in the new file that were added or
     * replaced by the patch. Only "+" lines inside hunks are considered; deletions ("-") and hunk context are not
     * recorded. Hunk headers of the form {@code @@ -a,b +c,d @@} advance the new-file line pointer to {@code c}.
     *
     * @param patch
     *         the unified diff text
     *
     * @return the set of 1-based line numbers in the new file that were added or replaced
     */
    @VisibleForTesting
    @SuppressWarnings({"PMD.CyclomaticComplexity", "StringSplitter"})
    Set<Integer> parseUnifiedDiffForNewFileAddedLines(final String patch) {
        Set<Integer> newFileChangedLines = new HashSet<>();
        int newLinePointer = -1;

        String[] lines = patch.split("\n");
        for (String raw : lines) {
            String line = stripTrailingCarriageReturn(raw);
            if (line.startsWith("@@")) {
                Matcher matcher = HUNK_REGEXP.matcher(line);
                if (matcher.matches()) {
                    newLinePointer = Integer.parseInt(matcher.group("newStart"));
                }
                continue;
            }

            if (newLinePointer < 0) { // before first hunk header
                continue;
            }

            if (line.isEmpty()) {
                continue;
            }

            char marker = line.charAt(0);
            switch (marker) {
                case '+' -> { // Added or replaced line in a new file
                    newFileChangedLines.add(newLinePointer);
                    newLinePointer++;
                }
                case ' ' -> newLinePointer++; // context line, advances the new file pointer
                default -> {
                }
            }
        }

        return newFileChangedLines;
    }

    private String normalize(final String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private String stripTrailingCarriageReturn(final String s) {
        if (s != null && !s.isEmpty() && s.charAt(s.length() - 1) == '\r') {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private boolean isBlank(final String s) {
        return s == null || s.isBlank();
    }

    private String safeLower(final String s) {
        return StringUtils.toRootLowerCase(s);
    }
}
