package edu.hm.hafner.grading.github;

import java.io.PrintStream;

import edu.hm.hafner.util.VisibleForTesting;

/**
 * GitHub action entrypoint for the autograding action.
 *
 * @author Tobias Effner
 * @author Ullrich Hafner
 */
public class QualityMonitor extends GitHubAutoGradingRunner {
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
    protected String getDefaultConfigurationPath() {
        return "/default-no-score-config.json";
    }
}
