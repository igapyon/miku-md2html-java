package jp.igapyon.mikumd2html.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class InlineMarkdownParser {
    private static final Pattern UNSAFE_CONTROL = Pattern.compile("[\\u0000-\\u001f\\u007f]");
    private final List<Diagnostic> diagnostics;
    private final int lineOffset;
    private final ImageEmbeddingSession images;

    public InlineMarkdownParser(List<Diagnostic> diagnostics, int lineOffset) {
        this(diagnostics, lineOffset, null);
    }

    public InlineMarkdownParser(List<Diagnostic> diagnostics, int lineOffset, ImageEmbeddingSession images) {
        this.diagnostics = diagnostics;
        this.lineOffset = lineOffset;
        this.images = images;
    }

    public String render(String source, int sourceLine) {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < source.length() && isEscapable(source.charAt(i + 1))) {
                html.append(HtmlEscape.text(String.valueOf(source.charAt(i + 1))));
                i += 2;
                continue;
            }
            if (c == '`') {
                int runEnd = i + 1;
                while (runEnd < source.length() && source.charAt(runEnd) == '`') runEnd++;
                String marker = source.substring(i, runEnd);
                int close = source.indexOf(marker, runEnd);
                if (close >= 0) {
                    String code = source.substring(runEnd, close).replace('\n', ' ');
                    if (code.length() >= 2 && code.startsWith(" ") && code.endsWith(" ") && !code.trim().isEmpty()) code = code.substring(1, code.length() - 1);
                    html.append("<code>").append(HtmlEscape.text(code)).append("</code>");
                    i = close + marker.length();
                    continue;
                }
            }
            if (c == '[' || (c == '!' && i + 1 < source.length() && source.charAt(i + 1) == '[')) {
                int imageStart = c == '!' ? i : -1;
                int bracket = imageStart >= 0 ? i + 1 : i;
                int closeBracket = findClosing(source, bracket, '[', ']');
                if (closeBracket >= 0 && closeBracket + 1 < source.length() && source.charAt(closeBracket + 1) == '(') {
                    int closeParen = findClosing(source, closeBracket + 1, '(', ')');
                    if (closeParen > closeBracket + 1) {
                        String label = source.substring(bracket + 1, closeBracket);
                        String destinationAndTitle = source.substring(closeBracket + 2, closeParen).trim();
                        LinkTarget target = destination(destinationAndTitle);
                        if (imageStart >= 0) {
                            if (images == null) {
                                Diagnostics.add(diagnostics, "error", "E_ASSET_RESOLVER_REQUIRED", "Image conversion is not initialized.",
                                        "markdown", new SourcePosition(sourceLine, i + 1));
                                html.append(HtmlEscape.text(plainText(label)));
                            } else {
                                html.append(images.renderImage(label, target.destination, target.title, sourceLine, i + 1));
                            }
                        } else if (safeLink(target.destination)) {
                            html.append("<a href=\"").append(HtmlEscape.attribute(target.destination)).append("\">")
                                    .append(render(label, sourceLine)).append("</a>");
                        } else {
                            Diagnostics.add(diagnostics, "warning", "W_LINK_REMOVED", "A link with an unsafe or unsupported URL was removed.",
                                    "markdown", new SourcePosition(sourceLine, i + 1));
                            html.append(render(label, sourceLine));
                        }
                        i = closeParen + 1;
                        continue;
                    }
                }
            }
            if (c == '*' || c == '_' || c == '~') {
                String delimiter = c == '~' && source.startsWith("~~", i) ? "~~"
                        : (source.startsWith("**", i) || source.startsWith("__", i)) ? source.substring(i, i + 2) : String.valueOf(c);
                int close = source.indexOf(delimiter, i + delimiter.length());
                if (close > i + delimiter.length() && !Character.isWhitespace(source.charAt(i + delimiter.length()))) {
                    String tag = "~~".equals(delimiter) ? "del" : delimiter.length() == 2 ? "strong" : "em";
                    html.append('<').append(tag).append('>').append(render(source.substring(i + delimiter.length(), close), sourceLine))
                            .append("</").append(tag).append('>');
                    i = close + delimiter.length();
                    continue;
                }
            }
            if (c == '<') {
                int close = source.indexOf('>', i + 1);
                if (close > i) {
                    String inside = source.substring(i + 1, close);
                    if (inside.matches("(?i)https?://[^\\s<>]+")) {
                        String url = inside;
                        html.append("<a href=\"").append(HtmlEscape.attribute(url)).append("\">")
                                .append(HtmlEscape.text(url)).append("</a>");
                        i = close + 1;
                        continue;
                    }
                    if (inside.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")) {
                        html.append("<a href=\"mailto:").append(HtmlEscape.attribute(inside)).append("\">")
                                .append(HtmlEscape.text(inside)).append("</a>");
                        i = close + 1;
                        continue;
                    }
                    if (inside.matches("/?[A-Za-z][A-Za-z0-9:-]*(?:\\s[^<>]*)?/?")) {
                        String sanitized = ArticleSanitizer.sanitizeInline(source.substring(i, close + 1), diagnostics, sourceLine);
                        html.append(sanitized);
                        i = close + 1;
                        continue;
                    }
                }
            }
            if (c == '&') {
                int entityEnd = source.indexOf(';', i + 1);
                if (entityEnd > i && entityEnd - i <= 12 && isEntity(source.substring(i + 1, entityEnd))) {
                    html.append(source, i, entityEnd + 1);
                    i = entityEnd + 1;
                    continue;
                }
            }
            html.append(HtmlEscape.text(String.valueOf(c)));
            i++;
        }
        return html.toString();
    }

    public static String plainText(String source) {
        String text = source.replaceAll("!\\[([^]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^]]+)\\]\\([^)]*\\)", "$1")
                .replaceAll("(`+)(.*?)\\1", "$2")
                .replaceAll("[*_~]", "")
                .replaceAll("<[^>]*>", "");
        return text.replace("\\*", "*").replace("\\_", "_").trim();
    }

    private static int findClosing(String text, int start, char open, char close) {
        int depth = 0;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == open) depth++;
            else if (c == close && --depth == 0) return i;
        }
        return -1;
    }

    private static final class LinkTarget {
        final String destination;
        final String title;
        LinkTarget(String destination, String title) { this.destination = destination; this.title = title; }
    }

    private static LinkTarget destination(String source) {
        String value = source.trim();
        if (source.startsWith("<")) {
            int end = value.indexOf('>');
            if (end > 0) return new LinkTarget(value.substring(1, end), title(value.substring(end + 1).trim()));
        }
        int space = value.indexOf(' ');
        if (space < 0) space = value.indexOf('\t');
        return space < 0 ? new LinkTarget(value, null)
                : new LinkTarget(value.substring(0, space), title(value.substring(space).trim()));
    }

    private static String title(String value) {
        if (value.length() < 2) return null;
        char first = value.charAt(0), last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'') || (first == '(' && last == ')')) {
            return value.substring(1, value.length() - 1);
        }
        return null;
    }

    private static boolean safeLink(String value) {
        String link = value.trim();
        if (link.isEmpty() || UNSAFE_CONTROL.matcher(link).find() || link.startsWith("//") || link.startsWith("\\")) return false;
        java.util.regex.Matcher scheme = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):").matcher(link);
        if (!scheme.find()) return true;
        String name = scheme.group(1).toLowerCase(Locale.ROOT);
        return "http".equals(name) || "https".equals(name) || "mailto".equals(name);
    }

    private static boolean isEntity(String value) {
        if (value.startsWith("#x") || value.startsWith("#X")) return value.substring(2).matches("[0-9A-Fa-f]+");
        if (value.startsWith("#")) return value.substring(1).matches("[0-9]+");
        return "amp".equals(value) || "lt".equals(value) || "gt".equals(value) || "quot".equals(value)
                || "apos".equals(value) || "nbsp".equals(value) || "copy".equals(value) || "reg".equals(value)
                || "mdash".equals(value) || "ndash".equals(value) || "hellip".equals(value) || "trade".equals(value);
    }

    private static boolean isEscapable(char c) {
        return "\\`*_{}[]()>#+-.!|~".indexOf(c) >= 0;
    }
}
