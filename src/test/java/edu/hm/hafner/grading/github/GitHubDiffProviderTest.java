package edu.hm.hafner.grading.github;

import edu.hm.hafner.util.FilteredLog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class GitHubDiffProviderTest {
    private Set<Integer> parse(final String diff) {
        try {
            var provider = new GitHubDiffProvider();
            Method m = GitHubDiffProvider.class.getDeclaredMethod("parseUnifiedDiffForNewFileAddedLines", String.class);
            m.setAccessible(true);
            @SuppressWarnings("unchecked") Set<Integer> result = (Set<Integer>) m.invoke(provider, diff);
            return result;
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void shouldParseAddedLines() {
        String diff = "@@ -1,3 +1,3 @@\n" +
                " line1\n" +
                "+added\n" +
                " line2\n";
        assertThat(parse(diff)).containsExactly(2);
    }

    @Test
    void shouldIgnoreDeletionsAndAdvanceContext() {
        String diff = "@@ -10,3 +20,3 @@\n" +
                " lineA\n" +
                "-deleted\n" +
                " lineB\n" +
                "+added\n" +
                " lineC\n";
        // New hunk starts at 20, first added occurs after two context/deletion lines advancing pointer to 22, then '+': 22
        assertThat(parse(diff)).containsExactly(22);
    }

    @Test
    void shouldHandleCrlf() {
        String diff = "@@ -1,1 +1,2 @@\r\n" +
                " line1\r\n" +
                "+added1\r\n" +
                "+added2\r\n";
        assertThat(parse(diff)).containsExactly(2, 3);
    }

    @Test
    void shouldHandleMultipleHunks() {
        String diff = "@@ -1,2 +5,2 @@\n" +
                "+a\n" +
                " b\n" +
                "@@ -10,2 +20,3 @@\n" +
                " c\n" +
                "+d\n" +
                "+e\n";
        assertThat(parse(diff)).containsExactly(5, 21, 22);
    }

    @Test
    void shouldReturnEmptyForNoPatchOrBlank() {
        assertThat(parse(null)).isEmpty();
        assertThat(parse("")).isEmpty();
        assertThat(parse("   ")).isEmpty();
    }

    @Test
    void shouldSkipHeadersAndUnknownLines() {
        String diff = "--- a/Foo.java\n" +
                "+++ b/Foo.java\n" +
                "@@ -1,1 +1,1 @@\n" +
                " ?\n" +
                "+x\n";
        assertThat(parse(diff)).containsExactly(2);
    }

    @Test
    void shouldParseEnvironmentFallback() {
        var provider = new GitHubDiffProvider();
        var log = new FilteredLog("test");

        var result = provider.loadFromEnvironment("a/b/C.java:1,2, 3 ; d/e/F.java:10,xyz,20", log);

        assertThat(result).containsOnly(
                entry("a/b/C.java", Set.of(1, 2, 3)),
                entry("d/e/F.java", Set.of(10, 20))
        );
    }
}


