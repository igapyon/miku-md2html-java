package jp.igapyon.mikumd2html.cli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CliArgumentsTest {
    @Test void acceptsSharedNodeOptionsAndDefaults() throws Exception {
        CliOptions options = CliArguments.parse(new String[] { "guide.md", "--out=guide.html", "--no-toc", "--force", "--max-output-mib", "8" });
        assertEquals(8L * 1_048_576L, options.maxOutputBytes);
        assertTrue(options.noToc);
        assertTrue(options.force);
        assertEquals("guide.html", options.output.toString());
        assertEquals("reference", options.imageMode);
        assertEquals("embed", CliArguments.parse(new String[] { "guide.md", "--out", "guide.html", "--image-mode", "embed" }).imageMode);
        assertEquals("reference", CliArguments.parse(new String[] { "guide.md", "--out", "guide.html", "--image-mode=reference" }).imageMode);
    }

    @Test void rejectsMissingOutputAndRepeatedFlags() {
        assertThrows(CliUsageException.class, () -> CliArguments.parse(new String[] { "guide.md" }));
        assertThrows(CliUsageException.class, () -> CliArguments.parse(new String[] { "guide.md", "--out", "a.html", "--force", "--force" }));
        assertThrows(CliUsageException.class, () -> CliArguments.parse(new String[] { "guide.md", "--out", "a.html", "--image-mode", "Reference" }));
        assertThrows(CliUsageException.class, () -> CliArguments.parse(new String[] { "guide.md", "--out", "a.html", "--image-mode", "embed", "--image-mode", "reference" }));
        assertThrows(CliUsageException.class, () -> CliArguments.parse(new String[] { "guide.md", "--out", "a.html", "--image-mode" }));
    }
}
