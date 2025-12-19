package edu.hm.hafner.grading.github;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class GitHubDiffProviderTest {
    @Test
    void shouldParseAddedLines() {
        String diff = """
                @@ -1,3 +1,3 @@
                 line1
                +added
                 line2
                """;
        assertThat(parse(diff)).containsExactly(2);
    }

    @Test
    void shouldIgnoreDeletionsAndAdvanceContext() {
        String diff = """
                @@ -10,3 +20,3 @@
                 lineA
                -deleted
                 lineB
                +added
                 lineC
                """;
        // New hunk starts at 20, first added occurs after two context/deletion lines advancing pointer to 22, then '+': 22
        assertThat(parse(diff)).containsExactly(22);
    }

    @Test
    @SuppressWarnings("StringConcatToTextBlock")
    void shouldHandleCrlf() {
        String diff = "@@ -1,1 +1,2 @@\r\n"
                + " line1\r\n"
                + "+added1\r\n"
                + "+added2\r\n";
        assertThat(parse(diff)).containsExactly(2, 3);
    }

    @Test
    void shouldHandleMultipleHunks() {
        String diff = """
                @@ -1,2 +5,2 @@
                +a
                 b
                @@ -10,2 +20,3 @@
                 c
                +d
                +e
                """;
        assertThat(parse(diff)).containsExactly(5, 21, 22);
    }

    @Test
    void shouldReturnEmptyForNoPatchOrBlank() {
        assertThat(parse("")).isEmpty();
        assertThat(parse("   ")).isEmpty();
    }

    @Test
    void shouldSkipHeadersAndUnknownLines() {
        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,1 +1,1 @@
                 ?
                +x
                """;
        assertThat(parse(diff)).containsExactly(2);
    }

    private Set<Integer> parse(final String diff) {
        var provider = new GitHubDiffProvider();
        return provider.parseUnifiedDiffForNewFileAddedLines(diff);
    }
}
