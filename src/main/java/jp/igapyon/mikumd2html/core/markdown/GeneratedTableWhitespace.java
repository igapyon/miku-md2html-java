package jp.igapyon.mikumd2html.core.markdown;

import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.ext.tables.TableBody;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

/** Tracks Markdown-generated tables without letting internal markers alter HTML source ranges. */
final class GeneratedTableWhitespace {
    static final class PreparedHtml {
        private final String html;
        private final Map<Integer, Integer> tableOffsetsAndInternalLineFeeds;

        PreparedHtml(String html, Map<Integer, Integer> tableOffsetsAndInternalLineFeeds) {
            this.html = html;
            this.tableOffsetsAndInternalLineFeeds = Collections.unmodifiableMap(
                    new LinkedHashMap<Integer, Integer>(tableOffsetsAndInternalLineFeeds));
        }

        String getHtml() { return html; }

        Map<Element, Integer> identify(List<org.jsoup.nodes.Node> roots) {
            Map<Integer, Element> elementsByOffset = new HashMap<Integer, Element>();
            for (org.jsoup.nodes.Node root : roots) collectTables(root, elementsByOffset);
            Map<Element, Integer> result = new IdentityHashMap<Element, Integer>();
            for (Map.Entry<Integer, Integer> entry : tableOffsetsAndInternalLineFeeds.entrySet()) {
                Integer offset = entry.getKey();
                Element element = elementsByOffset.get(offset);
                if (element == null) throw new IllegalStateException("Generated table was not preserved by HTML parsing at " + offset);
                result.put(element, entry.getValue());
            }
            return result;
        }

        private static void collectTables(org.jsoup.nodes.Node node, Map<Integer, Element> elementsByOffset) {
            if (node instanceof Element && "table".equals(((Element) node).normalName())) {
                org.jsoup.nodes.Range range = node.sourceRange();
                if (range != null && range.isTracked() && range.startPos() >= 0) {
                    Element previous = elementsByOffset.put(range.startPos(), (Element) node);
                    if (previous != null) throw new IllegalStateException("Multiple tables share an HTML source offset");
                }
            }
            for (org.jsoup.nodes.Node child : node.childNodes()) collectTables(child, elementsByOffset);
        }
    }

    private static final Pattern FORMATTING_WHITESPACE = Pattern.compile("^[ \\t\\n]*$");
    private static final Set<String> STRUCTURAL_CONTAINERS = new HashSet<String>(Arrays.asList(
            "body div blockquote ul ol li table thead tbody tfoot tr td th caption details dl dt dd".split(" ")));
    private static final Set<String> BLOCK_ELEMENTS = new HashSet<String>(Arrays.asList(
            "p div h1 h2 h3 h4 h5 h6 blockquote ul ol li pre hr table thead tbody tfoot tr td th caption dl dt dd details summary".split(" ")));

    private final String attributeName = "data-miku-generated-table-" + UUID.randomUUID().toString().replace("-", "");
    private final Map<Node, String> idsByNode = new IdentityHashMap<Node, String>();
    private final Map<String, Node> nodesById = new LinkedHashMap<String, Node>();
    private int nextId;

    String getAttributeName() { return attributeName; }

    synchronized String register(Node node) {
        String existing = idsByNode.get(node);
        if (existing != null) return existing;
        String id = "t" + nextId++;
        idsByNode.put(node, id);
        nodesById.put(id, node);
        return id;
    }

    PreparedHtml prepare(String html) {
        if (nodesById.isEmpty()) return new PreparedHtml(html, Collections.<Integer, Integer>emptyMap());
        Map<String, Integer> idToOffset = new HashMap<String, Integer>();
        List<Removal> removals = new ArrayList<Removal>();
        List<Tag> tags = scanTags(html);
        for (Tag tag : tags) {
            if (!tag.opening || !"table".equals(tag.name)) continue;
            List<AttributeToken> attributes = scanAttributes(html, tag);
            for (AttributeToken token : attributes) {
                if (!attributeName.equalsIgnoreCase(token.name)) continue;
                if (!nodesById.containsKey(token.value)) continue;
                if (idToOffset.put(token.value, tag.start) != null)
                    throw new IllegalStateException("Generated table marker was emitted more than once: " + token.value);
                removals.add(new Removal(token.start, token.end));
            }
        }
        if (idToOffset.size() != nodesById.size()) {
            Set<String> missing = new HashSet<String>(nodesById.keySet());
            missing.removeAll(idToOffset.keySet());
            throw new IllegalStateException("Generated table markers were not emitted: " + missing);
        }

        Collections.sort(removals, new Comparator<Removal>() {
            @Override public int compare(Removal left, Removal right) { return Integer.compare(left.start, right.start); }
        });
        StringBuilder clean = new StringBuilder(html.length());
        int cursor = 0;
        for (Removal removal : removals) {
            if (removal.start < cursor) throw new IllegalStateException("Overlapping generated table markers");
            clean.append(html, cursor, removal.start);
            cursor = removal.end;
        }
        clean.append(html, cursor, html.length());

        Map<Integer, Integer> cleanOffsets = new LinkedHashMap<Integer, Integer>();
        List<String> ids = new ArrayList<String>(idToOffset.keySet());
        Collections.sort(ids, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                return Integer.compare(idToOffset.get(left), idToOffset.get(right));
            }
        });
        for (String id : ids) {
            Integer offset = idToOffset.get(id);
            int removedBefore = 0;
            for (Removal removal : removals) if (removal.start < offset) removedBefore += removal.end - removal.start;
            int tableFormattingLineFeeds = countTableFormattingLineFeeds(nodesById.get(id));
            cleanOffsets.put(offset - removedBefore, tableFormattingLineFeeds);
        }
        return new PreparedHtml(clean.toString(), cleanOffsets);
    }

    private static int countTableFormattingLineFeeds(Node table) {
        int sectionCount = childCount(table);
        if (sectionCount == 0) return 0;
        int lineFeeds = sectionCount + 1;
        for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
            if (!(section instanceof TableHead) && !(section instanceof TableBody)) continue;
            int rowCount = childCount(section);
            lineFeeds += rowCount + 1;
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                if (row instanceof TableRow) lineFeeds += childCount(row) + 1;
            }
        }
        // One of the HAST boundary LFs is already represented by the surrounding
        // Flexmark block separator in the rendered fragment.
        return Math.max(0, lineFeeds - 1);
    }

    private static int childCount(Node parent) {
        int count = 0;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) count++;
        return count;
    }

    static void addRequiredWhitespace(Map<Element, Integer> generatedTables) {
        for (Map.Entry<Element, Integer> generated : generatedTables.entrySet()) {
            Element table = generated.getKey();
            Element parent = table.parent();
            if (parent == null || !STRUCTURAL_CONTAINERS.contains(parent.normalName())) continue;
            List<org.jsoup.nodes.Node> siblings = parent.childNodes();
            int tableIndex = siblings.indexOf(table);
            if (tableIndex < 0) continue;
            int whitespaceStart = tableIndex;
            while (whitespaceStart > 0 && isFormattingWhitespace(siblings.get(whitespaceStart - 1))) whitespaceStart--;
            org.jsoup.nodes.Node before = whitespaceStart == 0 ? null : siblings.get(whitespaceStart - 1);

            int lineFeeds = 0;
            StringBuilder existing = new StringBuilder();
            for (int i = whitespaceStart; i < tableIndex; i++) {
                String text = ((TextNode) siblings.get(i)).getWholeText();
                existing.append(text);
                for (int j = 0; j < text.length(); j++) if (text.charAt(j) == '\n') lineFeeds++;
            }
            boolean inlineBoundary = before != null && !isBlockElement(before);
            int targetLineFeeds = inlineBoundary ? lineFeeds + generated.getValue() : 2;
            if (targetLineFeeds <= lineFeeds) continue;

            int suffix = existing.length();
            while (suffix > 0 && (existing.charAt(suffix - 1) == ' ' || existing.charAt(suffix - 1) == '\t')) suffix--;
            StringBuilder replacement = new StringBuilder(existing.substring(0, suffix));
            for (int i = lineFeeds; i < targetLineFeeds; i++) replacement.append('\n');
            replacement.append(existing.substring(suffix));
            if (whitespaceStart == tableIndex) {
                parent.insertChildren(tableIndex, new TextNode(replacement.toString()));
            } else {
                ((TextNode) siblings.get(whitespaceStart)).text(replacement.toString());
                for (int i = tableIndex - 1; i > whitespaceStart; i--) siblings.get(i).remove();
            }
        }
    }

    private static boolean isFormattingWhitespace(org.jsoup.nodes.Node node) {
        return node instanceof TextNode && FORMATTING_WHITESPACE.matcher(((TextNode) node).getWholeText()).matches();
    }

    private static boolean isBlockElement(org.jsoup.nodes.Node node) {
        return node instanceof Element && BLOCK_ELEMENTS.contains(((Element) node).normalName());
    }

    private static List<Tag> scanTags(String html) {
        List<Tag> result = new ArrayList<Tag>();
        for (int index = 0; index < html.length();) {
            int start = html.indexOf('<', index);
            if (start < 0) break;
            if (html.startsWith("<!--", start)) {
                int close = html.indexOf("-->", start + 4);
                index = close < 0 ? html.length() : close + 3;
                continue;
            }
            if (start + 1 >= html.length()) break;
            char first = html.charAt(start + 1);
            if (first == '!' || first == '?') {
                int end = findTagEnd(html, start + 1);
                index = end < 0 ? html.length() : end + 1;
                continue;
            }
            boolean opening = first != '/';
            int nameStart = opening ? start + 1 : start + 2;
            while (nameStart < html.length() && Character.isWhitespace(html.charAt(nameStart))) nameStart++;
            if (nameStart >= html.length() || !isNameStart(html.charAt(nameStart))) {
                int end = findTagEnd(html, start + 1);
                index = end < 0 ? html.length() : end + 1;
                continue;
            }
            int nameEnd = nameStart;
            while (nameEnd < html.length() && isNameChar(html.charAt(nameEnd))) nameEnd++;
            int end = findTagEnd(html, nameEnd);
            if (end < 0) break;
            result.add(new Tag(start, nameEnd, end + 1, html.substring(nameStart, nameEnd).toLowerCase(java.util.Locale.ROOT), opening));
            index = end + 1;
        }
        return result;
    }

    private static int findTagEnd(String html, int from) {
        char quote = 0;
        for (int i = from; i < html.length(); i++) {
            char c = html.charAt(i);
            if (quote != 0) { if (c == quote) quote = 0; }
            else if (c == '\'' || c == '"') quote = c;
            else if (c == '>') return i;
        }
        return -1;
    }

    private List<AttributeToken> scanAttributes(String html, Tag tag) {
        List<AttributeToken> result = new ArrayList<AttributeToken>();
        int index = tag.nameEnd;
        while (index < tag.end - 1) {
            int whitespaceStart = index;
            while (index < tag.end - 1 && Character.isWhitespace(html.charAt(index))) index++;
            if (index >= tag.end - 1 || html.charAt(index) == '/') break;
            int nameStart = index;
            while (index < tag.end - 1 && !Character.isWhitespace(html.charAt(index))
                    && html.charAt(index) != '=' && html.charAt(index) != '/' && html.charAt(index) != '>') index++;
            if (index == nameStart) { index++; continue; }
            String name = html.substring(nameStart, index);
            while (index < tag.end - 1 && Character.isWhitespace(html.charAt(index))) index++;
            String value = "";
            if (index < tag.end - 1 && html.charAt(index) == '=') {
                index++;
                while (index < tag.end - 1 && Character.isWhitespace(html.charAt(index))) index++;
                if (index < tag.end - 1 && (html.charAt(index) == '\'' || html.charAt(index) == '"')) {
                    char quote = html.charAt(index++);
                    int valueStart = index;
                    while (index < tag.end - 1 && html.charAt(index) != quote) index++;
                    value = html.substring(valueStart, index);
                    if (index < tag.end - 1) index++;
                } else {
                    int valueStart = index;
                    while (index < tag.end - 1 && !Character.isWhitespace(html.charAt(index)) && html.charAt(index) != '>') index++;
                    value = html.substring(valueStart, index);
                }
            }
            if (attributeName.equalsIgnoreCase(name)) result.add(new AttributeToken(name, value, whitespaceStart, index));
        }
        return result;
    }

    private static boolean isNameStart(char c) { return Character.isLetter(c); }
    private static boolean isNameChar(char c) { return Character.isLetterOrDigit(c) || c == ':' || c == '-' || c == '_'; }

    private static final class Tag {
        final int start, nameEnd, end;
        final String name;
        final boolean opening;
        Tag(int start, int nameEnd, int end, String name, boolean opening) {
            this.start = start; this.nameEnd = nameEnd; this.end = end; this.name = name; this.opening = opening;
        }
    }

    private static final class AttributeToken {
        final String name, value;
        final int start, end;
        AttributeToken(String name, String value, int start, int end) {
            this.name = name; this.value = value; this.start = start; this.end = end;
        }
    }

    private static final class Removal {
        final int start, end;
        Removal(int start, int end) { this.start = start; this.end = end; }
    }
}
