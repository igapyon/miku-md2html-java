package jp.igapyon.mikumd2html.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineSvgTest {
    @Test void validatesAndEmbedsInlineSvgWithAccessibleAltText() {
        ConvertResult result = MikuMd2htmlCore.convertMarkdown(
                "<svg aria-label=\"circle\" viewBox=\"0 0 10 10\"><title>Circle</title><circle cx=\"5\" cy=\"5\" r=\"4\"/></svg>",
                "inline-svg.md", null);
        assertTrue(result.isSuccess(), () -> result.getDiagnostics().toString());
        assertEquals(1, result.getStats().getImageOccurrences());
        assertTrue(result.getHtml().contains("<img src=\"data:image/svg+xml;base64,"));
        assertTrue(result.getHtml().contains("alt=\"circle\""));
        assertFalse(result.getHtml().contains("<svg"));
    }

    @Test void rejectsInlineSvgEventHandlers() {
        ConvertResult result = MikuMd2htmlCore.convertMarkdown(
                "<svg aria-label=\"unsafe\" onload=\"run()\"><circle r=\"1\"/></svg>", "unsafe-svg.md", null);
        assertFalse(result.isSuccess());
        assertTrue(result.getDiagnostics().stream().anyMatch(d -> "E_SVG_UNSAFE".equals(d.getCode())));
    }
}
