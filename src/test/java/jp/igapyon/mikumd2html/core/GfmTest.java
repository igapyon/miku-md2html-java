package jp.igapyon.mikumd2html.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GfmTest {
    @Test void parsesTablesAutolinksStrikethroughTaskListsAndFootnotes() {
        String markdown = "~~old~~ https://example.com\n\n- [x] done\n\n| A | B |\n| :--- | ---: |\n| 1 | 2 |\n\nNote[^n].\n\n[^n]: Footnote text\n";
        ConvertResult result = MikuMd2htmlCore.convertMarkdown(markdown, "gfm.md", null);
        assertTrue(result.isSuccess(), () -> result.getDiagnostics().toString());
        assertTrue(result.getHtml().contains("<del>old</del>"));
        assertTrue(result.getHtml().contains("<a href=\"https://example.com\">https://example.com</a>"));
        assertTrue(result.getHtml().contains("<input type=\"checkbox\""));
        assertTrue(result.getHtml().contains("<table>"));
        assertTrue(result.getHtml().contains("data-miku-align=\"left\""));
        assertTrue(result.getHtml().contains("data-miku-align=\"right\""));
        assertTrue(result.getHtml().contains("Footnote text"));
        assertTrue(result.getHtml().contains("id=\"miku-fn-n\""), result.getHtml().substring(result.getHtml().indexOf("Footnote text") - 150, result.getHtml().indexOf("Footnote text") + 200));
        assertTrue(result.getHtml().contains("id=\"miku-fnref-n\""));
        assertTrue(result.getHtml().contains("href=\"#miku-fn-n\""));
        assertTrue(result.getHtml().contains("aria-describedby=\"miku-footnote-label\""));
        assertTrue(result.getHtml().contains("aria-label=\"Back to reference 1\""));
        assertFalse(result.getHtml().contains("footnote-backref"));
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "W_HTML_SANITIZED".equals(d.getCode())));
        assertEquals(1, result.getStats().getHeadingCount(), result.getHtml().substring(result.getHtml().indexOf("Footnote") - 100));
    }
}
