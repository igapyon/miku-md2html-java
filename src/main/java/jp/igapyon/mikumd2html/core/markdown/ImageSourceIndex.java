package jp.igapyon.mikumd2html.core.markdown;

import com.vladsch.flexmark.util.ast.Node;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import jp.igapyon.mikumd2html.core.SourcePosition;
import org.jsoup.nodes.Element;

/** Bridges generated Flexmark image tags back to their original Markdown spans. */
final class ImageSourceIndex {
    static final class Span {
        final SourcePosition start;
        final SourcePosition end;

        Span(SourcePosition start, SourcePosition end) {
            this.start = start;
            this.end = end;
        }
    }

    private final String source;
    private final int lineOffset;
    private final String attributeName = "data-miku-image-source-" + UUID.randomUUID().toString().replace("-", "");
    private final Map<Node, String> idsByNode = new IdentityHashMap<Node, String>();
    private final Map<String, Span> spansById = new LinkedHashMap<String, Span>();
    private int nextId;

    ImageSourceIndex(String source, int lineOffset) {
        this.source = source;
        this.lineOffset = lineOffset;
    }

    String getAttributeName() {
        return attributeName;
    }

    synchronized String register(Node node) {
        String existing = idsByNode.get(node);
        if (existing != null) return existing;
        int startOffset = node.getStartOffset();
        int endOffset = node.getEndOffset();
        if (startOffset < 0 || endOffset < startOffset || endOffset > source.length()) return null;
        String id = "i" + nextId++;
        spansById.put(id, new Span(positionAt(startOffset), positionAt(endOffset)));
        idsByNode.put(node, id);
        return id;
    }

    synchronized Span consume(Element element) {
        String id = element.attr(attributeName);
        if (id.isEmpty()) return null;
        Span span = spansById.remove(id);
        if (span != null) element.removeAttr(attributeName);
        return span;
    }

    private SourcePosition positionAt(int offset) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new SourcePosition(lineOffset + line, column);
    }
}
