package jp.igapyon.mikumd2html.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JDK-only CommonMark/GFM parser for the upstream contract subset being ported. */
public final class MarkdownParser {
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})(?:[ \\t]+|$)(.*?)[ \\t]*#*[ \\t]*$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern LIST = Pattern.compile("^( {0,3})([-+*]|[0-9]+[.)])[ \\t]+(.*)$");
    private static final Pattern RULE = Pattern.compile("^ {0,3}(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})$");
    private final List<Diagnostic> diagnostics;
    private final int lineOffset;
    private final InlineMarkdownParser inline;
    private final List<DocumentStructure.Heading> headings = new ArrayList<DocumentStructure.Heading>();
    private final Set<String> headingIds = new HashSet<String>();

    public MarkdownParser(List<Diagnostic> diagnostics, int lineOffset) {
        this(diagnostics, lineOffset, null);
    }

    public MarkdownParser(List<Diagnostic> diagnostics, int lineOffset, ImageEmbeddingSession images) {
        this.diagnostics = diagnostics;
        this.lineOffset = lineOffset;
        this.inline = new InlineMarkdownParser(diagnostics, lineOffset, images);
    }

    public MarkdownDocument parse(String markdown) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder html = new StringBuilder();
        parseBlocks(lines, 0, lines.length, html);
        return new MarkdownDocument(html.toString(), headings);
    }

    private void parseBlocks(String[] lines, int start, int end, StringBuilder html) {
        int i = start;
        while (i < end) {
            String line = lines[i];
            if (line.trim().isEmpty()) { i++; continue; }

            Matcher fence = FENCE.matcher(line);
            if (fence.matches()) {
                String marker = fence.group(1);
                String info = fence.group(2).trim();
                int close = i + 1;
                StringBuilder code = new StringBuilder();
                while (close < end) {
                    String candidate = lines[close];
                    if (candidate.matches("^ {0,3}" + Pattern.quote(String.valueOf(marker.charAt(0))) + "{" + marker.length() + ",}\\s*$")) break;
                    if (code.length() > 0) code.append('\n');
                    code.append(candidate);
                    close++;
                }
                String language = CodeLanguages.resolve(info);
                html.append("<pre><code");
                if (language != null) html.append(" class=\"language-").append(HtmlEscape.attribute(language)).append("\"");
                html.append('>').append(HtmlEscape.text(code.toString())).append("</code></pre>\n");
                i = close < end ? close + 1 : end;
                continue;
            }

            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                int level = heading.group(1).length();
                String source = heading.group(2).trim();
                String text = InlineMarkdownParser.plainText(source);
                String slug = Slug.slugify(text);
                String base = "miku-h-" + slug;
                String id = base;
                int suffix = 2;
                while (!headingIds.add(id)) id = base + "-" + suffix++;
                headings.add(new DocumentStructure.Heading(level, text, slug, id));
                html.append("<h").append(level).append(" id=\"").append(id).append("\">")
                        .append(inline.render(source, lineOffset + i + 1)).append("</h").append(level).append(">\n");
                i++;
                continue;
            }

            if (RULE.matcher(line).matches()) { html.append("<hr>\n"); i++; continue; }

            if (line.matches("^ {0,3}>.*$")) {
                List<String> quote = new ArrayList<String>();
                int j = i;
                while (j < end && lines[j].matches("^ {0,3}>.*$")) {
                    quote.add(lines[j].replaceFirst("^ {0,3}> ?", ""));
                    j++;
                }
                html.append("<blockquote>\n");
                parseBlocks(quote.toArray(new String[quote.size()]), 0, quote.size(), html);
                html.append("</blockquote>\n");
                i = j;
                continue;
            }

            if (i + 1 < end && line.indexOf('|') >= 0 && isTableDelimiter(lines[i + 1])) {
                i = parseTable(lines, i, end, html);
                continue;
            }

            Matcher list = LIST.matcher(line);
            if (list.matches()) { i = parseList(lines, i, end, html, list.group(2)); continue; }

            if (ArticleSanitizer.looksLikeHtml(line)) {
                int rawLine = i;
                String tag = line.trim().replaceFirst("^</?([A-Za-z][A-Za-z0-9:-]*).*$", "$1").toLowerCase(java.util.Locale.ROOT);
                if ("script".equals(tag) || "style".equals(tag) || "iframe".equals(tag) || "object".equals(tag) || "form".equals(tag)) {
                    String closeTag = "</" + tag;
                    if (line.toLowerCase(java.util.Locale.ROOT).contains(closeTag)) {
                        i++;
                    } else {
                        i++;
                        while (i < end && !lines[i].toLowerCase(java.util.Locale.ROOT).contains(closeTag)) i++;
                        if (i < end) i++;
                    }
                    Diagnostics.add(diagnostics, "warning", "W_HTML_SANITIZED", "An active HTML element and its contents were removed: " + tag + ".",
                            "markdown", new SourcePosition(lineOffset + rawLine + 1, 1));
                } else {
                    html.append("<p>").append(ArticleSanitizer.sanitizeInline(line.trim(), diagnostics, lineOffset + rawLine + 1)).append("</p>\n");
                    i++;
                }
                continue;
            }

            StringBuilder paragraph = new StringBuilder(line.trim());
            int j = i + 1;
            while (j < end && !lines[j].trim().isEmpty() && !startsBlock(lines, j, end)) {
                paragraph.append(' ').append(lines[j].trim());
                j++;
            }
            html.append("<p>").append(inline.render(paragraph.toString(), lineOffset + i + 1)).append("</p>\n");
            i = j;
        }
    }

    private int parseList(String[] lines, int start, int end, StringBuilder html, String marker) {
        Matcher first = LIST.matcher(lines[start]);
        boolean ordered = marker.matches("[0-9]+[.)]");
        String tag = ordered ? "ol" : "ul";
        html.append('<').append(tag).append(">\n");
        int i = start;
        while (i < end) {
            Matcher item = LIST.matcher(lines[i]);
            if (!item.matches() || item.group(2).matches("[0-9]+[.)]") != ordered) break;
            String text = item.group(3);
            boolean checked = false;
            boolean task = false;
            if (text.startsWith("[ ] ") || text.startsWith("[x] ") || text.startsWith("[X] ")) {
                task = true;
                checked = !"[ ] ".equals(text.substring(0, 4));
                text = text.substring(4);
            }
            html.append("<li>");
            if (task) html.append("<input type=\"checkbox\" disabled").append(checked ? " checked" : "").append(">");
            html.append(inline.render(text, lineOffset + i + 1)).append("</li>\n");
            i++;
        }
        html.append("</").append(tag).append(">\n");
        return i;
    }

    private int parseTable(String[] lines, int start, int end, StringBuilder html) {
        List<String> headers = tableCells(lines[start]);
        List<String> alignments = tableCells(lines[start + 1]);
        html.append("<table><thead><tr>");
        for (int col = 0; col < headers.size(); col++) {
            String align = col < alignments.size() ? alignment(alignments.get(col)) : "";
            html.append("<th");
            if (!align.isEmpty()) html.append(" data-miku-align=\"").append(align).append("\"");
            html.append('>').append(inline.render(headers.get(col).trim(), lineOffset + start + 1)).append("</th>");
        }
        html.append("</tr></thead><tbody>\n");
        int i = start + 2;
        while (i < end && lines[i].indexOf('|') >= 0 && !lines[i].trim().isEmpty()) {
            List<String> cells = tableCells(lines[i]);
            html.append("<tr>");
            for (int col = 0; col < headers.size(); col++) {
                String align = col < alignments.size() ? alignment(alignments.get(col)) : "";
                html.append("<td");
                if (!align.isEmpty()) html.append(" data-miku-align=\"").append(align).append("\"");
                html.append('>');
                if (col < cells.size()) html.append(inline.render(cells.get(col).trim(), lineOffset + i + 1));
                html.append("</td>");
            }
            html.append("</tr>\n");
            i++;
        }
        html.append("</tbody></table>\n");
        return i;
    }

    private static boolean isTableDelimiter(String line) {
        if (line.indexOf('|') < 0) return false;
        for (String cell : tableCells(line)) if (!cell.trim().matches(":?-{1,}:?")) return false;
        return true;
    }

    private static List<String> tableCells(String row) {
        String value = row.trim();
        if (value.startsWith("|")) value = value.substring(1);
        if (value.endsWith("|") && !value.endsWith("\\|")) value = value.substring(0, value.length() - 1);
        List<String> cells = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) { cell.append(c); escaped = false; }
            else if (c == '\\') escaped = true;
            else if (c == '|') { cells.add(cell.toString()); cell.setLength(0); }
            else cell.append(c);
        }
        if (escaped) cell.append('\\');
        cells.add(cell.toString());
        return cells;
    }

    private static String alignment(String delimiter) {
        String value = delimiter.trim();
        boolean left = value.startsWith(":");
        boolean right = value.endsWith(":");
        if (left && right) return "center";
        if (left) return "left";
        if (right) return "right";
        return "";
    }

    private boolean startsBlock(String[] lines, int i, int end) {
        String line = lines[i];
        return HEADING.matcher(line).matches() || FENCE.matcher(line).matches() || RULE.matcher(line).matches()
                || line.matches("^ {0,3}>.*$") || LIST.matcher(line).matches() || ArticleSanitizer.looksLikeHtml(line)
                || (i + 1 < end && line.indexOf('|') >= 0 && isTableDelimiter(lines[i + 1]));
    }
}
