package jp.igapyon.mikumd2html.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrontMatterParserTest {
    @Test void parsesYaml12BlockScalarsAndIgnoresUnknownNestedValues() {
        String source = "---\ntitle: >\n  Hello\n  World\ndescription: |\n  A guide\n  for everyone\nsettings:\n  nested: [one, two]\n  enabled: true\n---\n# Body";
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        FrontMatter result = new FrontMatterParser().parse(source, diagnostics);
        assertTrue(result.isValid());
        assertEquals("Hello World", result.getTitle());
        assertEquals("A guide for everyone", result.getDescription());
        assertEquals("# Body", result.getBody());
        assertTrue(diagnostics.isEmpty());
    }

    @Test void treatsEmptyTitleAsMissingAndFallsBackToFirstHeading() {
        ConvertResult result = MikuMd2htmlCore.convertMarkdown("---\ntitle: \"\"\n---\n# Body", "guide.md", null);
        assertTrue(result.isSuccess());
        assertEquals("Body", result.getTitle());
    }

    @Test void rejectsDuplicateKeys() {
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        FrontMatter result = new FrontMatterParser().parse("---\ntitle: first\ntitle: second\n---\n# Body", diagnostics);
        assertFalse(result.isValid());
        assertEquals("E_FRONT_MATTER", diagnostics.get(0).getCode());
    }

    @Test void treatsTheFirstClosingMarkerAsTheEndOfFrontMatter() {
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        FrontMatter result = new FrontMatterParser().parse("---\ntitle: first\n---\ntitle: second\n---\n# Body", diagnostics);
        assertTrue(result.isValid());
        assertEquals("first", result.getTitle());
        assertEquals("title: second\n---\n# Body", result.getBody());
    }

    @Test void warnsForNonStringMetadataAndIncludesFieldDetails() {
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        FrontMatter result = new FrontMatterParser().parse("---\ntitle: 42\nlang: true\n---\nBody", diagnostics);
        assertTrue(result.isValid());
        assertNull(result.getTitle());
        assertNull(result.getLang());
        assertEquals(2, diagnostics.size());
        assertEquals("title", diagnostics.get(0).getDetails().get("field"));
        assertEquals("lang", diagnostics.get(1).getDetails().get("field"));
    }
}
