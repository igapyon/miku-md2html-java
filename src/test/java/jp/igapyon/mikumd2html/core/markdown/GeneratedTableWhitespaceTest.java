package jp.igapyon.mikumd2html.core.markdown;

import com.vladsch.flexmark.ext.tables.TableBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedTableWhitespaceTest {
    @Test void removesOnlyRegisteredMarkerBytesAndKeepsTableSourceOffsets() {
        GeneratedTableWhitespace index = new GeneratedTableWhitespace();
        TableBlock block = new TableBlock();
        String id = index.register(block);
        assertEquals(id, index.register(block), "render callbacks for one AST node must be idempotent");
        String marker = index.getAttributeName();
        String source = "<p>Before</p>\n<table " + marker + "=\"" + id + "\"><tr><td>x</td></tr></table>";
        String expected = "<p>Before</p>\n<table><tr><td>x</td></tr></table>";

        GeneratedTableWhitespace.PreparedHtml prepared = index.prepare(source);

        assertEquals(expected, prepared.getHtml());
        assertEquals(source.length() - expected.length(), marker.length() + id.length() + 4);
        List<Node> nodes = parse(prepared.getHtml());
        Map<Element, Integer> tables = prepared.identify(nodes);
        assertEquals(1, tables.size());
        assertEquals(expected.indexOf("<table"), tables.keySet().iterator().next().sourceRange().startPos());
    }

    @Test void ignoresUnregisteredMarkerValuesAndLookalikeAttributes() {
        GeneratedTableWhitespace index = new GeneratedTableWhitespace();
        String id = index.register(new TableBlock());
        String marker = index.getAttributeName();
        String source = "<table data-miku-generated-table-test=\"t0\"><tr><td>raw</td></tr></table>"
                + "<table " + marker + "=\"user\" " + marker + "=\"" + id
                + "\"><tr><td>generated</td></tr></table>";

        GeneratedTableWhitespace.PreparedHtml prepared = index.prepare(source);

        assertTrue(prepared.getHtml().contains(marker + "=\"user\""));
        assertEquals(1, prepared.identify(parse(prepared.getHtml())).size());
    }

    @Test void failsClosedWhenARegisteredTableWasNotRendered() {
        GeneratedTableWhitespace index = new GeneratedTableWhitespace();
        index.register(new TableBlock());
        assertThrows(IllegalStateException.class, () -> index.prepare("<p>no table</p>"));
    }

    @Test void addsOnlyMissingStructuralWhitespaceBeforeGeneratedTables() {
        Element body = fragment("<table><tr><td>first</td></tr></table><p>After</p>\n<table><tr><td>second</td></tr></table>");
        List<Element> tables = body.select("table");
        Map<Element, Integer> generated = new java.util.IdentityHashMap<Element, Integer>();
        for (Element table : tables) generated.put(table, 12);

        GeneratedTableWhitespace.addRequiredWhitespace(generated);

        assertEquals("\n\n", ((TextNode) body.childNode(0)).getWholeText());
        assertEquals("\n\n", ((TextNode) body.childNode(3)).getWholeText());
        assertEquals("After", body.selectFirst("p").text());
    }

    @Test void leavesInlineBoundariesAndRawTablesAlone() {
        GeneratedTableWhitespace index = new GeneratedTableWhitespace();
        String id = index.register(new TableBlock());
        String marker = index.getAttributeName();
        String source = "<table><tr><td><em>x</em><table " + marker + "=\"" + id
                + "\"><tr><td>generated</td></tr></table></td></tr></table>";
        GeneratedTableWhitespace.PreparedHtml prepared = index.prepare(source);
        Element body = fragment(prepared.getHtml());
        Element outerTable = body.select("table").first();
        Element innerTable = body.select("table").last();
        Map<Element, Integer> generated = prepared.identify(body.childNodes());

        GeneratedTableWhitespace.addRequiredWhitespace(generated);

        assertEquals(2, body.select("table").size());
        assertTrue(outerTable.select("table").contains(innerTable), "the raw outer table remains in place");
        assertEquals("x", innerTable.previousElementSibling().text(), "an inline sibling must not trigger block spacing");
    }

    private static List<Node> parse(String html) {
        Parser parser = Parser.htmlParser().setTrackPosition(true);
        Document document = Document.createShell("");
        return parser.parseFragmentInput(html, document.body(), "");
    }

    private static Element fragment(String html) {
        Element body = new Element("body");
        Parser parser = Parser.htmlParser().setTrackPosition(true);
        for (Node node : parser.parseFragmentInput(html, body, "")) body.appendChild(node);
        return body;
    }
}
