package jp.igapyon.mikumd2html.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression cases copied from the pinned Node v0.6.0 html-output contract. */
class HtmlOutputV060Test {
    @Test void placesTocAfterLeadingDirectH1AndBeforeArticleOtherwise() {
        ConvertResult leading = convert("# 設計\n\nIntro\n\n## Same\n\n## Same\n\n[Jump](#設計)", true);
        String leadingHtml = leading.getHtml();
        int article = leadingHtml.indexOf("<article>");
        int h1 = leadingHtml.indexOf("<h1 id=\"miku-h-設計\">");
        int toc = leadingHtml.indexOf("<details class=\"miku-md2html-toc\" open>");
        assertTrue(leading.isSuccess());
        assertTrue(article >= 0 && article < h1 && h1 < toc);
        assertEquals(3, leading.getStats().getHeadingCount());

        ConvertResult later = convert("Intro\n\n# Title", true);
        String laterHtml = later.getHtml();
        assertTrue(laterHtml.indexOf("<details class=\"miku-md2html-toc\" open>")
                < laterHtml.indexOf("<article>"));
    }

    @Test void limitsFormattingBlankLinesAfterRemovedCommentsAndScripts() {
        ConvertResult comments = convert("# Title\n\n<!-- first -->\n\n<!-- second -->\n\nParagraph", false);
        assertEquals("\n<h1 id=\"miku-h-title\">Title</h1>\n\n<p>Paragraph</p>\n", article(comments.getHtml()));

        ConvertResult scripts = convert("# Title\n\n<script>first()</script>\n\n<script>second()</script>\n\nParagraph", false);
        assertEquals("\n<h1 id=\"miku-h-title\">Title</h1>\n\n<p>Paragraph</p>\n", article(scripts.getHtml()));
        assertEquals(2, scripts.getDiagnostics().stream().filter(item -> "W_HTML_SANITIZED".equals(item.getCode())).count());
    }

    @Test void keepsBothArticleBoundaryNewlinesForEmptyMarkdown() {
        ConvertResult empty = convert("", false);
        assertEquals("\n\n", article(empty.getHtml()));
    }

    @Test void limitsWhitespaceInsideContainersAndPreservesCodeText() {
        ConvertResult nested = convert("<div>\n<p>First</p>\n<script>first()</script>\n<script>second()</script>\n<p>Second</p>\n</div>", true);
        assertEquals("\n<div>\n<p>First</p>\n\n<p>Second</p>\n</div>\n", article(nested.getHtml()));

        ConvertResult code = convert("<pre><code>first\n\n\nsecond</code></pre>", true);
        assertTrue(article(code.getHtml()).contains("<pre><code>first\n\n\nsecond</code></pre>"));
    }

    private static ConvertResult convert(String markdown, boolean toc) {
        ConvertOptions options = new ConvertOptions();
        options.setToc(toc);
        return MikuMd2htmlCore.convertMarkdown(markdown, "fixture.md", options);
    }

    private static String article(String html) {
        int start = html.indexOf("<article>");
        int end = html.indexOf("</article>", start);
        assertTrue(start >= 0 && end > start, "HTML should contain an article");
        return html.substring(start + "<article>".length(), end);
    }
}
