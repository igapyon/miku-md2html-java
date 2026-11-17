package jp.igapyon.mikumd2html.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.Range;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

/** Limits only structural formatting whitespace while preserving the serialized article bytes. */
final class HtmlStructuralWhitespace {
    private static final Pattern FORMATTING_WHITESPACE = Pattern.compile("^[ \\t\\n]*$");
    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\\n(?:[ \\t]*\\n){2,}");
    private static final Set<String> STRUCTURAL_CONTAINERS = new HashSet<String>(Arrays.asList(
            "div blockquote ul ol li table thead tbody tfoot tr td th caption dl dt dd details".split(" ")));
    private static final Set<String> BLOCK_ELEMENTS = new HashSet<String>(Arrays.asList(
            "p div h1 h2 h3 h4 h5 h6 blockquote ul ol li pre hr table thead tbody tfoot tr td th caption dl dt dd details summary".split(" ")));

    private HtmlStructuralWhitespace() { }

    static int firstTopLevelH1End(String html) {
        if (html == null) return -1;
        Parser parser = Parser.htmlParser().setTrackPosition(true);
        Document document = Document.createShell("");
        List<Node> nodes = parser.parseFragmentInput(html, document.body(), "");
        for (Node node : nodes) {
            if (isFormattingWhitespace(node)) continue;
            if (!(node instanceof Element) || !"h1".equals(((Element) node).normalName())) return -1;
            Range end = ((Element) node).endSourceRange();
            if (end == null || !end.isTracked() || end.endPos() < 0 || end.endPos() > html.length()) return -1;
            return end.endPos();
        }
        return -1;
    }

    static String normalizeArticle(String html) {
        // Rendered blocks already carry their closing formatting newline. An empty
        // fragment needs both template boundary newlines, as it has no block output.
        String source = "\n" + html + (html.isEmpty() ? "\n" : "");
        Parser parser = Parser.htmlParser().setTrackPosition(true);
        Document document = Document.createShell("");
        List<Node> nodes = parser.parseFragmentInput(source, document.body(), "");
        for (Node node : nodes) document.body().appendChild(node);

        List<Replacement> replacements = new ArrayList<Replacement>();
        Deque<Element> pending = new ArrayDeque<Element>();
        pending.push(document.body());
        while (!pending.isEmpty()) {
            Element parent = pending.pop();
            List<Node> children = parent.childNodes();
            boolean root = parent == document.body();
            if (root || STRUCTURAL_CONTAINERS.contains(parent.normalName())) {
                collectWhitespaceReplacements(source, children, root, replacements);
            }
            for (Node child : children) {
                if (!(child instanceof Element)) continue;
                String name = ((Element) child).normalName();
                if (!"pre".equals(name) && !"code".equals(name)) pending.push((Element) child);
            }
        }

        Collections.sort(replacements, new Comparator<Replacement>() {
            @Override public int compare(Replacement left, Replacement right) {
                return Integer.compare(right.start, left.start);
            }
        });
        StringBuilder result = new StringBuilder(source);
        for (Replacement replacement : replacements) {
            result.replace(replacement.start, replacement.end, replacement.value);
        }
        return result.toString();
    }

    private static void collectWhitespaceReplacements(String source, List<Node> children, boolean root,
            List<Replacement> replacements) {
        for (int index = 0; index < children.size();) {
            Node first = children.get(index);
            if (!isFormattingWhitespace(first)) {
                index++;
                continue;
            }
            int endIndex = index + 1;
            while (endIndex < children.size() && isFormattingWhitespace(children.get(endIndex))) endIndex++;
            Node before = index == 0 ? null : children.get(index - 1);
            Node after = endIndex == children.size() ? null : children.get(endIndex);
            boolean blockNeighbor = isBlockElement(before) || isBlockElement(after);
            if (isBlockBoundary(before) && isBlockBoundary(after) && (root || blockNeighbor)) {
                StringBuilder value = new StringBuilder();
                for (int cursor = index; cursor < endIndex; cursor++) value.append(((TextNode) children.get(cursor)).getWholeText());
                String normalized = EXTRA_BLANK_LINES.matcher(value).replaceAll("\n\n");
                if (!normalized.equals(value.toString())) {
                    Range start = children.get(index).sourceRange();
                    Range finish = children.get(endIndex - 1).sourceRange();
                    if (start != null && finish != null && start.isTracked() && finish.isTracked()
                            && start.startPos() >= 0 && finish.endPos() >= start.startPos()
                            && finish.endPos() <= source.length()) {
                        replacements.add(new Replacement(start.startPos(), finish.endPos(), normalized));
                    }
                }
            }
            index = endIndex;
        }
    }

    private static boolean isFormattingWhitespace(Node node) {
        return node instanceof TextNode && FORMATTING_WHITESPACE.matcher(((TextNode) node).getWholeText()).matches();
    }

    private static boolean isBlockBoundary(Node node) {
        return node == null || isBlockElement(node);
    }

    private static boolean isBlockElement(Node node) {
        return node instanceof Element && BLOCK_ELEMENTS.contains(((Element) node).normalName());
    }

    private static final class Replacement {
        final int start;
        final int end;
        final String value;

        Replacement(int start, int end, String value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }
}
