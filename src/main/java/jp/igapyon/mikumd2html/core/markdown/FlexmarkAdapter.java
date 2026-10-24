package jp.igapyon.mikumd2html.core.markdown;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.footnotes.Footnote;
import com.vladsch.flexmark.ext.footnotes.FootnoteBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;
import jp.igapyon.mikumd2html.core.ArticleSanitizer;
import jp.igapyon.mikumd2html.core.Diagnostic;
import jp.igapyon.mikumd2html.core.Diagnostics;
import jp.igapyon.mikumd2html.core.DocumentStructure;
import jp.igapyon.mikumd2html.core.HtmlEscape;
import jp.igapyon.mikumd2html.core.ImageEmbeddingSession;
import jp.igapyon.mikumd2html.core.MarkdownDocument;
import jp.igapyon.mikumd2html.core.Slug;
import jp.igapyon.mikumd2html.core.SourcePosition;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Comment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Internal adapter: only the CommonMark/GFM parser profile is exposed to the product core. */
public final class FlexmarkAdapter {
    private static final Pattern HEADING = Pattern.compile("(?s)<h([1-6])>(.*?)</h\\1>");
    private static final Pattern ACTIVE_HTML = Pattern.compile("(?is)<\\s*(script|style|iframe|object|embed|form|button|audio|video|source|select|textarea|option|template|base|meta|link)\\b");
    private static final Set<String> ALLOWED_TAGS = new HashSet<String>(Arrays.asList(
            "p div span h1 h2 h3 h4 h5 h6 br hr em strong del s ins u sub sup code pre kbd samp blockquote ul ol li a img table thead tbody tfoot tr th td caption details summary dl dt dd input".split(" ")));
    private static final Set<String> DROPPED_TAGS = new HashSet<String>(Arrays.asList(
            "script style link base meta iframe object embed form button audio video source select textarea option template".split(" ")));

    private final List<Diagnostic> diagnostics;
    private final int lineOffset;
    private final ImageEmbeddingSession images;
    private final Map<String, Integer> diagnosedUnsafeLinks = new LinkedHashMap<String, Integer>();

    public FlexmarkAdapter(List<Diagnostic> diagnostics, int lineOffset, ImageEmbeddingSession images) {
        this.diagnostics = diagnostics;
        this.lineOffset = lineOffset;
        this.images = images;
    }

    public MarkdownDocument parse(String markdown) {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.<Extension>asList(
                TablesExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                FootnoteExtension.create()));
        options.set(HtmlRenderer.SOFT_BREAK, " ");
        options.set(HtmlRenderer.HARD_BREAK, "<br>\n");
        options.set(HtmlRenderer.RENDER_HEADER_ID, false);
        options.set(HtmlRenderer.GENERATE_HEADER_ID, false);

        Parser parser = Parser.builder(options).build();
        Node tree = parser.parse(markdown);
        collectUnsafeLinks(tree);
        Map<Integer, String> footnoteLabels = collectFootnoteLabels(tree);
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        String html = renderer.render(tree);
        collectSuppressedActiveHtml(tree, html);
        html = adaptFootnotes(html, footnoteLabels, tree);
        html = normalizeGeneratedHtml(html);
        html = sanitizeHtmlFragment(html);
        List<DocumentStructure.Heading> headings = new ArrayList<DocumentStructure.Heading>();
        String article = assignHeadingIds(html, headings);
        return new MarkdownDocument(article, headings);
    }

    /** Flexmark omits some raw active HTML before the rendered HTML reaches jsoup. */
    private void collectSuppressedActiveHtml(Node root, String renderedHtml) {
        Map<String, Integer> renderedCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> sourceCounts = new LinkedHashMap<String, Integer>();
        Matcher rendered = ACTIVE_HTML.matcher(renderedHtml);
        while (rendered.find()) {
            String tag = rendered.group(1).toLowerCase(java.util.Locale.ROOT);
            renderedCounts.put(tag, count(renderedCounts, tag) + 1);
        }
        for (Node node : root.getDescendants()) {
            if (!(node instanceof HtmlBlock) && !(node instanceof HtmlInline)) continue;
            Matcher matcher = ACTIVE_HTML.matcher(node.getChars().toString());
            while (matcher.find()) {
                String tag = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
                int seen = count(sourceCounts, tag) + 1;
                sourceCounts.put(tag, seen);
                if (seen <= count(renderedCounts, tag)) continue;
                // The HTML sanitizer reports elements that survive rendering. Report only
                // source elements Flexmark already suppressed, to avoid duplicate warnings.
                // A raw user tag cannot be supplied by the generated document shell here.
                com.vladsch.flexmark.util.misc.Pair<Integer, Integer> location = node.lineColumnAtStart();
                Diagnostics.add(diagnostics, "warning", "W_HTML_SANITIZED",
                        "An active HTML element and its contents were removed: " + tag + ".", "markdown",
                        new SourcePosition(Math.max(1, lineOffset + location.getFirst()), Math.max(1, location.getSecond())));
            }
        }
    }

    private static int count(Map<String, Integer> counts, String key) {
        Integer value = counts.get(key);
        return value == null ? 0 : value;
    }

    private String sanitizeHtmlFragment(String html) {
        org.jsoup.parser.Parser parser = org.jsoup.parser.Parser.htmlParser().setTrackPosition(true);
        Document document = Document.createShell("");
        List<org.jsoup.nodes.Node> fragment = parser.parseFragmentInput(html, document.body(), "");
        for (org.jsoup.nodes.Node node : fragment) document.body().appendChild(node);
        document.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.html)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.base);
        sanitizeChildren(document.body());
        String sanitized = document.body().html();
        sanitized = sanitized.replaceAll("(?i)(<input\\b[^>]*>)&nbsp;", "$1 ");
        return sanitized.replace("&amp;", "&#x26;");
    }

    private void sanitizeChildren(Element parent) {
        for (org.jsoup.nodes.Node node : new ArrayList<org.jsoup.nodes.Node>(parent.childNodes())) {
            if (node instanceof Comment) {
                node.remove();
                Diagnostics.add(diagnostics, "warning", "W_HTML_SANITIZED", "An HTML comment was removed.",
                        "markdown", sourcePosition(node));
                continue;
            }
            if (!(node instanceof Element)) continue;
            Element element = (Element) node;
            String tag = element.normalName();
            if ("svg".equals(tag)) {
                replaceInlineSvg(element);
                continue;
            }
            if (DROPPED_TAGS.contains(tag)) {
                Diagnostics.add(diagnostics, "warning", "W_HTML_SANITIZED",
                        "An active HTML element and its contents were removed: " + tag + ".", "markdown", sourcePosition(element));
                element.remove();
                continue;
            }
            if (!ALLOWED_TAGS.contains(tag)) {
                Diagnostics.add(diagnostics, "warning", "W_HTML_SANITIZED",
                        "An unsupported HTML tag was unwrapped: " + tag + ".", "markdown", sourcePosition(element));
                sanitizeChildren(element);
                element.unwrap();
                continue;
            }
            sanitizeAttributes(element);
            if ("img".equals(tag)) {
                replaceImage(element);
                continue;
            }
            sanitizeChildren(element);
        }
    }

    private void sanitizeAttributes(Element element) {
        String tag = element.normalName();
        String originalClass = element.attr("class");
        boolean generatedTaskInput = "input".equals(tag) && "task-list-item-checkbox".equals(originalClass);
        List<String> removed = new ArrayList<String>();
        for (Attribute attribute : new ArrayList<Attribute>(element.attributes().asList())) {
            String key = attribute.getKey().toLowerCase(java.util.Locale.ROOT);
            String value = attribute.getValue();
            if ("class".equals(key) && ("contains-task-list".equals(value)
                    || "task-list-item".equals(value) || "task-list-item-checkbox".equals(value))) {
                element.removeAttr(key);
                continue;
            }
            if (generatedTaskInput && "readonly".equals(key)) {
                element.removeAttr(key);
                continue;
            }
            boolean keep = "title".equals(key);
            if ("a".equals(tag) && "href".equals(key)) {
                if (ArticleSanitizer.isSafeLink(value)) keep = true;
                else {
                    element.removeAttr(key);
                    if (!consumeDiagnosedUnsafeLink(value)) {
                        Diagnostics.add(diagnostics, "warning", "W_LINK_REMOVED",
                                "A link with an unsafe or unsupported URL was removed.", "markdown", sourcePosition(element));
                    }
                    continue;
                }
            }
            if ("a".equals(tag) && "id".equals(key) && value.matches("miku-fnref-[A-Za-z0-9_.:-]+(?:-[0-9]+)?")) keep = true;
            if ("a".equals(tag) && "aria-describedby".equals(key) && "miku-footnote-label".equals(value)) keep = true;
            if ("a".equals(tag) && "aria-label".equals(key) && value.matches("Back to reference [0-9]+")) keep = true;
            if ("img".equals(tag) && "src".equals(key)) keep = true;
            if ("img".equals(tag) && "alt".equals(key)) keep = true;
            if ("img".equals(tag) && ("width".equals(key) || "height".equals(key)) && positiveInteger(value)) keep = true;
            if (("td".equals(tag) || "th".equals(tag)) && ("colspan".equals(key) || "rowspan".equals(key)) && positiveInteger(value)) keep = true;
            if (("td".equals(tag) || "th".equals(tag)) && "align".equals(key) && isAlignment(value)) {
                element.attr("data-miku-align", value.toLowerCase(java.util.Locale.ROOT));
                element.removeAttr(key);
                continue;
            }
            if (("td".equals(tag) || "th".equals(tag)) && "data-miku-align".equals(key) && isAlignment(value)) keep = true;
            if ("details".equals(tag) && "open".equals(key)) keep = true;
            if ("ol".equals(tag) && "start".equals(key) && integer(value)) keep = true;
            if ("li".equals(tag) && "id".equals(key) && value.matches("miku-fn-[A-Za-z0-9_.:-]+")) keep = true;
            if ("input".equals(tag) && "type".equals(key) && "checkbox".equalsIgnoreCase(value)) keep = true;
            if ("input".equals(tag) && "disabled".equals(key)) keep = true;
            if ("input".equals(tag) && "checked".equals(key)) keep = true;
            if (!keep) { element.removeAttr(key); removed.add(key); }
        }
        if ("input".equals(tag) && (!"checkbox".equalsIgnoreCase(element.attr("type")) || !element.hasAttr("disabled"))) {
            Diagnostics.add(diagnostics, "warning", "W_HTML_SANITIZED",
                    "An interactive or unsupported input element was removed.", "markdown", sourcePosition(element));
            element.remove();
            return;
        }
        if (!removed.isEmpty()) {
            java.util.Collections.sort(removed);
            Diagnostics.add(diagnostics, "warning", "W_HTML_SANITIZED",
                    "Unsupported HTML attributes were removed: " + String.join(", ", removed) + ".",
                    "markdown", sourcePosition(element), java.util.Collections.<String, Object>singletonMap("count", removed.size()));
        }
    }

    private void replaceImage(Element element) {
        String src = element.attr("src");
        if (src.isEmpty()) {
            Diagnostics.add(diagnostics, "error", "E_IMAGE_URI", "Image source is missing.", "markdown", sourcePosition(element));
            element.remove();
            return;
        }
        String alt = element.hasAttr("alt") ? element.attr("alt") : "";
        String title = element.hasAttr("title") ? element.attr("title") : null;
        String imageHtml = images.renderImage(alt, src, title, line(sourcePosition(element)), column(sourcePosition(element)));
        List<org.jsoup.nodes.Node> replacement = org.jsoup.parser.Parser.parseFragment(imageHtml, element.parent(), "");
        if (replacement.isEmpty() || !(replacement.get(0) instanceof Element)) {
            element.removeAttr("src");
            return;
        }
        Element rendered = (Element) replacement.get(0);
        if (rendered.hasAttr("src")) element.attr("src", rendered.attr("src"));
        else element.removeAttr("src");
    }

    private void replaceInlineSvg(Element svg) {
        String source = svg.outerHtml();
        if (!source.matches("(?is)^<svg[^>]*\\bxmlns\\s*=")) source = source.replaceFirst("(?i)^<svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"");
        String alt = svg.hasAttr("aria-label") ? svg.attr("aria-label") : svg.selectFirst("title") == null ? "" : svg.selectFirst("title").text();
        String data = "data:image/svg+xml;base64," + java.util.Base64.getEncoder().encodeToString(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String imageHtml = images.renderImage(alt, data, null, line(sourcePosition(svg)), column(sourcePosition(svg)));
        List<org.jsoup.nodes.Node> replacement = org.jsoup.parser.Parser.parseFragment(imageHtml, svg.parent(), "");
        if (replacement.isEmpty()) svg.remove();
        else svg.replaceWith(replacement.get(0));
    }

    private SourcePosition sourcePosition(org.jsoup.nodes.Node node) {
        org.jsoup.nodes.Range range = node.sourceRange();
        if (range == null || !range.isTracked() || !range.start().isTracked()) return null;
        return new SourcePosition(Math.max(1, lineOffset + range.start().lineNumber()), Math.max(1, range.start().columnNumber()));
    }
    private static int line(SourcePosition p) { return p == null ? 1 : p.getLine(); }
    private static int column(SourcePosition p) { return p == null ? 1 : p.getColumn(); }
    private static boolean positiveInteger(String value) { try { long n = Long.parseLong(value); return n > 0 && n <= 1_000_000_000L; } catch (RuntimeException ex) { return false; } }
    private static boolean integer(String value) { try { Long.parseLong(value); return true; } catch (RuntimeException ex) { return false; } }
    private static boolean isAlignment(String value) { return "left".equalsIgnoreCase(value) || "center".equalsIgnoreCase(value) || "right".equalsIgnoreCase(value); }

    private boolean consumeDiagnosedUnsafeLink(String href) {
        String key = href == null ? "" : href.trim();
        Integer count = diagnosedUnsafeLinks.get(key);
        if (count == null || count == 0) return false;
        if (count == 1) diagnosedUnsafeLinks.remove(key);
        else diagnosedUnsafeLinks.put(key, count - 1);
        return true;
    }

    private void collectUnsafeLinks(Node root) {
        for (Node node : root.getDescendants()) {
            if (!(node instanceof Link)) continue;
            String href = ((Link) node).getUrl().toString();
            if (ArticleSanitizer.isSafeLink(href)) continue;
            Integer count = diagnosedUnsafeLinks.get(href);
            diagnosedUnsafeLinks.put(href, count == null ? 1 : count + 1);
            com.vladsch.flexmark.util.misc.Pair<Integer, Integer> location = node.lineColumnAtStart();
            Diagnostics.add(diagnostics, "warning", "W_LINK_REMOVED",
                    "A link with an unsafe or unsupported URL was removed.", "markdown",
                    new SourcePosition(Math.max(1, lineOffset + location.getFirst()), Math.max(1, location.getSecond())));
        }
    }

    private static Map<Integer, String> collectFootnoteLabels(Node root) {
        Map<Integer, String> labels = new LinkedHashMap<Integer, String>();
        for (Node node : root.getDescendants()) {
            if (node instanceof FootnoteBlock) {
                FootnoteBlock block = (FootnoteBlock) node;
                labels.put(block.getFootnoteOrdinal() + 1, block.getText().toString().trim());
            }
        }
        return labels;
    }

    private String adaptFootnotes(String html, Map<Integer, String> labels, Node root) {
        String open = "<div class=\"footnotes\">";
        int container = html.indexOf(open);
        if (container < 0) return html;

        Map<Integer, String> firstReference = new LinkedHashMap<Integer, String>();
        List<String> referenceIds = new ArrayList<String>();
        Map<String, Integer> occurrences = new LinkedHashMap<String, Integer>();
        for (Node node : root.getDescendants()) {
            if (!(node instanceof Footnote)) continue;
            Footnote footnote = (Footnote) node;
            int ordinal = footnote.getReferenceOrdinal() + 1;
            String label = labels.get(ordinal);
            if (label == null || label.isEmpty()) continue;
            int occurrence = occurrences.containsKey(label) ? occurrences.get(label) + 1 : 1;
            occurrences.put(label, occurrence);
            String id = "miku-fnref-" + label + (occurrence == 1 ? "" : "-" + occurrence);
            if (!firstReference.containsKey(ordinal)) firstReference.put(ordinal, id);
            referenceIds.add(id);
        }

        Pattern footnoteTarget = Pattern.compile("(?i)(href=\"#|id=\")(fn(?:ref)?)-([0-9]+)(\")");
        Matcher targets = footnoteTarget.matcher(html);
        StringBuffer rewritten = new StringBuffer();
        while (targets.find()) {
            int ordinal = Integer.parseInt(targets.group(3));
            String label = labels.get(ordinal);
            if (label == null || label.isEmpty()) continue;
            String target = "fnref".equalsIgnoreCase(targets.group(2))
                    ? firstReference.get(ordinal) : "miku-fn-" + label;
            if (target == null) continue;
            targets.appendReplacement(rewritten, Matcher.quoteReplacement(targets.group(1) + target + targets.group(4)));
        }
        targets.appendTail(rewritten);
        html = rewritten.toString();

        Pattern footnoteReference = Pattern.compile("(?is)<sup id=\"miku-fnref-([^\"]+)\"><a class=\"footnote-ref\" href=\"([^\"]+)\">(.*?)</a></sup>");
        Matcher references = footnoteReference.matcher(html);
        StringBuffer movedReferenceIds = new StringBuffer();
        while (references.find()) {
            String replacement = "<sup><a href=\"" + references.group(2) + "\" id=\"miku-fnref-" + references.group(1)
                    + "\" aria-describedby=\"miku-footnote-label\">" + references.group(3) + "</a></sup>";
            references.appendReplacement(movedReferenceIds, Matcher.quoteReplacement(replacement));
        }
        references.appendTail(movedReferenceIds);
        html = movedReferenceIds.toString();
        Pattern backReference = Pattern.compile("(?i)<a href=\"#miku-fnref-([^\"]+)\" class=\"footnote-backref\">");
        Matcher backReferences = backReference.matcher(html);
        StringBuffer labeledBackReferences = new StringBuffer();
        Map<String, Integer> backReferenceCounts = new LinkedHashMap<String, Integer>();
        while (backReferences.find()) {
            String label = backReferences.group(1);
            int count = backReferenceCounts.containsKey(label) ? backReferenceCounts.get(label) + 1 : 1;
            backReferenceCounts.put(label, count);
            backReferences.appendReplacement(labeledBackReferences, Matcher.quoteReplacement(
                    "<a href=\"#miku-fnref-" + label + "\" aria-label=\"Back to reference " + count + "\">"));
        }
        backReferences.appendTail(labeledBackReferences);
        html = labeledBackReferences.toString();

        Matcher inlineBackReferences = Pattern.compile(
                "</p>\\s*(<a href=\"#miku-fnref-[^\"]+\" aria-label=\"Back to reference [0-9]+\">.*?</a>)").matcher(html);
        StringBuffer inlineFootnotes = new StringBuffer();
        while (inlineBackReferences.find()) {
            inlineBackReferences.appendReplacement(inlineFootnotes,
                    Matcher.quoteReplacement(" " + inlineBackReferences.group(1) + "</p>"));
        }
        inlineBackReferences.appendTail(inlineFootnotes);
        html = inlineFootnotes.toString();

        container = html.indexOf(open);
        String footnoteContent = html.substring(container + open.length())
                .replaceFirst("(?is)^\\s*<hr\\s*/?>\\s*", "");
        html = html.substring(0, container) + "<h2>Footnotes</h2>\n" + footnoteContent;
        int close = html.lastIndexOf("</div>");
        if (close >= 0) html = html.substring(0, close) + html.substring(close + "</div>".length());
        Diagnostics.add(diagnostics, "warning", "W_HTML_SANITIZED",
                "An unsupported HTML tag was unwrapped: section.", null, null);
        return html;
    }

    private static String normalizeGeneratedHtml(String html) {
        String result = html.replace("<hr />", "<hr>");
        Matcher tables = Pattern.compile("(?is)<table\\b[^>]*>.*?</table>").matcher(result);
        StringBuffer compactTables = new StringBuffer();
        while (tables.find()) {
            tables.appendReplacement(compactTables, Matcher.quoteReplacement(tables.group().replaceAll(">\\s+<", "><")));
        }
        tables.appendTail(compactTables);
        result = compactTables.toString();
        Matcher alignments = Pattern.compile("(?i)(<(?:td|th)\\b[^>]*?)\\s+align=\"(left|center|right)\"", Pattern.CASE_INSENSITIVE).matcher(result);
        StringBuffer aligned = new StringBuffer();
        while (alignments.find()) {
            alignments.appendReplacement(aligned, Matcher.quoteReplacement(alignments.group(1) + " data-miku-align=\""
                    + alignments.group(2).toLowerCase(java.util.Locale.ROOT) + "\""));
        }
        alignments.appendTail(aligned);
        return aligned.toString();
    }

    private static String assignHeadingIds(String html, List<DocumentStructure.Heading> headings) {
        Matcher matcher = HEADING.matcher(html);
        StringBuffer result = new StringBuffer();
        Set<String> used = new HashSet<String>();
        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            String contents = matcher.group(2);
            String text = Jsoup.parseBodyFragment(contents).text().trim();
            String slug = Slug.slugify(text);
            String base = "miku-h-" + slug;
            String id = base;
            int suffix = 2;
            while (!used.add(id)) id = base + "-" + suffix++;
            headings.add(new DocumentStructure.Heading(level, text, slug, id));
            String replacement = "<h" + level + " id=\"" + HtmlEscape.attribute(id) + "\">" + contents + "</h" + level + ">";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
