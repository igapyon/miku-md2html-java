package jp.igapyon.mikumd2html.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleSanitizerTest {
    @Test void keepsSafeRawHtmlAndUnwrapsOrRemovesUnsafeParts() {
        String markdown = "<details open><summary>Details</summary><custom>inside</custom></details>\n\n"
                + "<div id=\"miku-h-spoof\" class=\"miku-md2html-copy\" onclick=\"run()\">safe</div>\n\n"
                + "<!-- hidden -->";
        ConvertResult result = MikuMd2htmlCore.convertMarkdown(markdown, "raw.html.md", null);
        assertTrue(result.isSuccess(), () -> result.getDiagnostics().toString());
        assertTrue(result.getHtml().contains("<details open><summary>Details</summary>inside</details>"));
        assertTrue(result.getHtml().contains(">safe</div>"));
        assertFalse(result.getHtml().contains("miku-h-spoof"));
        assertFalse(result.getHtml().contains("miku-md2html-copy\""));
        assertFalse(result.getHtml().contains("onclick"));
        assertFalse(result.getHtml().contains("<!-- hidden -->"));
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "W_HTML_SANITIZED".equals(d.getCode())));
    }

    @Test void routesRawImagesThroughTheImageEmbeddingSession() {
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/odcAAAAASUVORK5CYII=";
        ConvertResult result = MikuMd2htmlCore.convertMarkdown(
                "<img src=\"data:image/png;base64," + png + "\" alt=\"pixel\" title=\"tiny\">", "raw-image.md", null);
        assertTrue(result.isSuccess(), () -> result.getDiagnostics().toString());
        assertEquals(1, result.getStats().getImageOccurrences());
        assertEquals(1, result.getStats().getUniqueAssets());
        assertTrue(result.getHtml().contains("<img src=\"data:image/png;base64," + png + "\" alt=\"pixel\" title=\"tiny\">"));
    }
}
