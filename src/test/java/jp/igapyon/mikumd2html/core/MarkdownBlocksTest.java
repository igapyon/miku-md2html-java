package jp.igapyon.mikumd2html.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownBlocksTest {
    @Test void parsesSetextHeadingsHardBreaksReferenceLinksAndNestedLists() {
        ConvertResult result = MikuMd2htmlCore.convertMarkdown(
                "Heading\n=======\n\nFirst  \nSecond\n\n[guide][ref]\n\n[ref]: https://example.com \"Guide\"\n\n3. outer\n   - inner\n",
                "blocks.md", null);
        assertTrue(result.isSuccess(), () -> result.getDiagnostics().toString());
        assertEquals("Heading", result.getTitle());
        assertTrue(result.getHtml().contains("<h1 id=\"miku-h-heading\">Heading</h1>"));
        assertTrue(result.getHtml().contains("First<br>\nSecond"));
        assertTrue(result.getHtml().contains("<a href=\"https://example.com\" title=\"Guide\">guide</a>"));
        assertTrue(result.getHtml().contains("<ol start=\"3\">"));
        assertTrue(result.getHtml().contains("<li>outer\n<ul>"));
        assertTrue(result.getHtml().contains("<li>inner</li>"));
    }

    @Test void removesUnsafeGeneratedLinkButKeepsItsText() {
        ConvertResult result = MikuMd2htmlCore.convertMarkdown("[bad](javascript:alert(1))", "link.md", null);
        assertTrue(result.isSuccess());
        assertTrue(result.getHtml().contains(">bad</p>"), result.getHtml());
        assertFalse(result.getHtml().contains("javascript:"));
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "W_LINK_REMOVED".equals(d.getCode())));
    }
}
