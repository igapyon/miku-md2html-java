package jp.igapyon.mikumd2html.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MikuMd2htmlCoreTest {
    @Test void convertsMarkdownAndUsesNodeTitleAndHeadingRules() {
        ConvertResult result = MikuMd2htmlCore.convertMarkdown("# Hello World\n\nA **small** paragraph.", "guide.md", null);
        assertTrue(result.isSuccess());
        assertEquals("Hello World", result.getTitle());
        assertTrue(result.getHtml().contains("<h1 id=\"miku-h-hello-world\">Hello World</h1>"));
        assertTrue(result.getHtml().contains("<strong>small</strong>"));
        assertTrue(result.getHtml().contains("miku-md2html-toc"));
        assertEquals(1, result.getStats().getHeadingCount());
    }

    @Test void readsFrontMatterAndHonorsExplicitOverrides() {
        String markdown = "---\ntitle: Front title\nlang: en-US\ndescription: Short text\n---\n# Heading\n";
        ConvertResult fromFrontMatter = MikuMd2htmlCore.convertMarkdown(markdown, "guide.md", null);
        assertTrue(fromFrontMatter.isSuccess());
        assertEquals("Front title", fromFrontMatter.getTitle());
        assertEquals("en-US", fromFrontMatter.getLang());
        assertTrue(fromFrontMatter.getHtml().contains("name=\"description\" content=\"Short text\""));

        ConvertOptions options = new ConvertOptions();
        options.setTitle("CLI title"); options.setLang("ja-JP"); options.setToc(false);
        ConvertResult overridden = MikuMd2htmlCore.convertMarkdown(markdown, "guide.md", options);
        assertEquals("CLI title", overridden.getTitle());
        assertEquals("ja-JP", overridden.getLang());
        assertFalse(overridden.getHtml().contains("<details class=\"miku-md2html-toc\""));
    }

    @Test void embedsBase64PngAndReportsOccurrenceStats() {
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/odcAAAAASUVORK5CYII=";
        String markdown = "![pixel](data:image/png;base64," + png + ")\n\n![pixel again](data:image/png;base64," + png + ")";
        ConvertResult result = MikuMd2htmlCore.convertMarkdown(markdown, "images.md", null);
        assertTrue(result.isSuccess(), () -> result.getDiagnostics().toString());
        assertEquals(2, result.getStats().getImageOccurrences());
        assertEquals(1, result.getStats().getUniqueAssets());
        assertTrue(result.getHtml().contains("<img src=\"data:image/png;base64," + png));
    }

    @Test void rejectsActiveRawHtmlAndUnsafeLinksWithDiagnostics() {
        ConvertResult result = MikuMd2htmlCore.convertMarkdown("<script>bad()</script>\n\n[bad](javascript:alert(1))", "safe.md", null);
        assertTrue(result.isSuccess());
        assertFalse(result.getHtml().contains("<script>bad"));
        assertFalse(result.getHtml().contains("javascript:"));
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "W_HTML_SANITIZED".equals(d.getCode())));
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "W_LINK_REMOVED".equals(d.getCode())));
    }

    @Test void acceptsEmptyMarkdownAndUsesTheFilenameAsTitle() {
        ConvertResult result = MikuMd2htmlCore.convertMarkdown("", "empty.md", null);
        assertTrue(result.isSuccess(), () -> result.getDiagnostics().toString());
        assertEquals("empty", result.getTitle());
        assertTrue(result.getHtml().contains("<body>"));
        assertEquals(0, result.getStats().getHeadingCount());
    }

    @Test void collapsesRunsOfHyphensInHeadingSlugs() {
        ConvertResult result = MikuMd2htmlCore.convertMarkdown("# a--b\n\n# a b\n\n[go](#a--b)", "slug.md", null);
        assertTrue(result.isSuccess());
        assertTrue(result.getHtml().contains("id=\"miku-h-a-b\">a--b</h1>"));
        assertTrue(result.getHtml().contains("id=\"miku-h-a-b-2\">a b</h1>"));
        assertTrue(result.getHtml().contains("href=\"#miku-h-a-b\">go</a>"));
    }

    @Test void reportsRemoteImagesSeparatelyFromUnsupportedImageSchemes() {
        for (String destination : new String[] { "https://example.invalid/a.png", "//example.invalid/a.png" }) {
            ConvertResult result = MikuMd2htmlCore.convertMarkdown("![remote](" + destination + ")", "remote.md", null);
            assertFalse(result.isSuccess());
            assertEquals("E_REMOTE_IMAGE", result.getDiagnostics().get(0).getCode());
        }
    }
}
