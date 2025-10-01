package edu.hm.hafner.grading.github;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import edu.hm.hafner.util.FilteredLog;

/**
 * Provides changed lines for a GitHub pull request so patch coverage can be computed.
 *
 * <p>Calls the GitHub REST API to list PR files and parses unified diffs from each file's {@code patch}
 * field into per-file 1-based line numbers for the new file (added or replaced lines only). Renamed files
 * are keyed by the new filename.</p>
 */
class GitHubDiffProvider {
    /**
     * Loads changed lines per file from GitHub. If mandatory parameters are missing, falls back to the
     * optional {@code PATCH_CHANGED_LINES} environment format for local testing.
     *
     * @param repository the {@code owner/repo}
     * @param prNumber   the pull request number
     * @param token      the GitHub token
     * @param apiUrl     optional alternative API URL
     * @param log        logger
     *
     * @return a mapping of repository-relative file path to a set of 1-based changed line numbers
     */
    Map<String, Set<Integer>> loadChangedLines(final String repository, final String prNumber,
                                               final String token, final String apiUrl,
                                               final FilteredLog log) {
        if (isBlank(repository) || isBlank(prNumber) || isBlank(token)) {
            String raw = System.getenv("PATCH_CHANGED_LINES");
            if (raw != null && !raw.isBlank()) {
                log.logInfo("Patch coverage: Using PATCH_CHANGED_LINES fallback (missing repo/pr/token)");
                return loadFromEnvironment(raw, log);
            }
            log.logInfo("Patch coverage: Missing inputs and no PATCH_CHANGED_LINES; skipping");
            return Collections.emptyMap();
        }

        Map<String, Set<Integer>> changedLinesByPath = new HashMap<>();

        try {
            GitHubBuilder builder = new GitHubBuilder().withOAuthToken(token);
            if (!isBlank(apiUrl)) {
                builder.withEndpoint(apiUrl);
            }
            GitHub github = builder.build();

            int number = Integer.parseInt(prNumber);
            int filesWithChanges = 0;

            for (GHPullRequestFileDetail file : github.getRepository(repository).getPullRequest(number).listFiles()) {
                String status = safeLower(file.getStatus());
                String newPath = normalize(file.getFilename()); // use new filename for renames
                String oldPath = normalize(file.getPreviousFilename());

                // Skip removed files; added/modified/renamed/copied are relevant
                if ("removed".equals(status)) {
                    continue;
                }

                String patch = file.getPatch();
                if (patch == null || patch.isBlank()) {
                    if (!oldPath.isBlank()) {
                        log.logInfo("Patch coverage: Skipping file without patch (possibly binary/large): %s (status=%s, previous=%s)", newPath, status, oldPath);
                    }
                    else {
                        log.logInfo("Patch coverage: Skipping file without patch (possibly binary/large): %s (status=%s)", newPath, status);
                    }
                    continue;
                }

                if (!oldPath.isBlank() && ("renamed".equals(status) || "copied".equals(status))) {
                    log.logInfo("Patch coverage: Processing %s file: %s (previous=%s)", status, newPath, oldPath);
                }

                Set<Integer> lines = parseUnifiedDiffForNewFileAddedLines(patch);
                if (!lines.isEmpty()) {
                    changedLinesByPath.put(newPath, lines);
                    filesWithChanges++;
                }
            }

            log.logInfo("Patch coverage: Loaded changed lines for %d file(s) from PR#%s", filesWithChanges, prNumber);
        }
        catch (IOException | NumberFormatException exception) {
            log.logException(exception, "Patch coverage: Failed to load changed lines from GitHub");
            return Collections.emptyMap();
        }

        return changedLinesByPath;
    }

    /**
     * Parses a unified diff text for one file and returns the 1-based line numbers in the new file that were added
     * or replaced by the patch. Only "+" lines inside hunks are considered; deletions ("-") and hunk context are
     * not recorded. Hunk headers of the form {@code @@ -a,b +c,d @@} advance the new-file line pointer to {@code c}.
     */
    private Set<Integer> parseUnifiedDiffForNewFileAddedLines(final String patch) {
        Set<Integer> newFileChangedLines = new HashSet<>();
        if (patch == null || patch.isBlank()) {
            return newFileChangedLines;
        }

        Pattern hunk = Pattern.compile("^@@ -(?<oldStart>\\d+)(?:,\\d+)? \\+(?<newStart>\\d+)(?:,\\d+)? @@.*$");
        int newLinePointer = -1;

        String[] lines = patch.split("\n");
        for (String raw : lines) {
            String line = stripTrailingCarriageReturn(raw);
            if (line.startsWith("@@")) {
                Matcher m = hunk.matcher(line);
                if (m.matches()) {
                    newLinePointer = Integer.parseInt(m.group("newStart"));
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
                case '+':
                    // Added or replaced line in new file
                    newFileChangedLines.add(newLinePointer);
                    newLinePointer++;
                    break;
                case ' ': // context line, advances new file pointer
                    newLinePointer++;
                    break;
                case '-': // deletion in old file; does not advance new pointer
                    break;
                case '\\': // "\\ No newline at end of file" marker
                    break;
                default:
                    // Headers like "+++" or "---" may appear; ignore.
                    break;
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
        return s == null ? "" : s.toLowerCase(java.util.Locale.ENGLISH);
    }

    /**
     * Utility to build a mapping for single-file experiments without hitting the network.
     * Accepts a semicolon-separated list of entries in the form {@code path:1,2,3} in the
     * {@code PATCH_CHANGED_LINES} environment variable and converts it to a map.
     */
    Map<String, Set<Integer>> loadFromEnvironment(final String raw, final FilteredLog log) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Set<Integer>> result = new HashMap<>();
        for (String entry : raw.split(";")) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String path = parts[0].trim();
            Set<Integer> lines = new HashSet<>();
            for (String num : parts[1].split(",")) {
                try {
                    int line = Integer.parseInt(num.trim());
                    if (line > 0) {
                        lines.add(line);
                    }
                }
                catch (NumberFormatException ignored) {
                    // ignore
                }
            }
            if (!path.isEmpty() && !lines.isEmpty()) {
                result.put(path, lines);
            }
        }
        log.logInfo("Patch coverage: Parsed %d file(s) from PATCH_CHANGED_LINES", result.size());
        return result;
    }
}
