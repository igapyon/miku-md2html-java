package jp.igapyon.mikumd2html.core;

import java.util.List;
import java.util.regex.Pattern;

/** Shared link and legacy inline-HTML policy helpers for the conversion pipeline. */
public final class ArticleSanitizer {
    private static final Pattern TAG = Pattern.compile("</?([A-Za-z][A-Za-z0-9:-]*)(?:\\s[^<>]*?)?/?>");
    private static final Pattern ACTIVE_BLOCK = Pattern.compile("(?is)<(script|style|iframe|object|embed|form|template)\\b[^>]*>.*?</\\1\\s*>");

    private ArticleSanitizer() { }

    public static String sanitizeInline(String value, List<Diagnostic> diagnostics, int line) {
        String withoutActive = ACTIVE_BLOCK.matcher(value).replaceAll("");
        StringBuffer output = new StringBuffer();
        java.util.regex.Matcher matcher = TAG.matcher(withoutActive);
        boolean removed = false;
        while (matcher.find()) {
            removed = true;
            matcher.appendReplacement(output, "");
        }
        matcher.appendTail(output);
        if (removed || !withoutActive.equals(value)) {
            Diagnostics.add(diagnostics, "warning", "W_HTML_SANITIZED", "Author-supplied HTML was unwrapped by the JDK-only sanitizer.",
                    "markdown", new SourcePosition(Math.max(1, line), 1));
        }
        return HtmlEscape.text(output.toString());
    }

    public static boolean looksLikeHtml(String line) {
        return line != null && line.matches("(?s)^\\s*</?[A-Za-z][A-Za-z0-9:-]*(?:\\s[^<>]*)?/?>.*$");
    }

    public static boolean isSafeLink(String value) {
        String link = value == null ? "" : value.trim();
        if (link.isEmpty() || link.matches("(?s).*[\\u0000-\\u001f\\u007f].*") || link.startsWith("//") || link.startsWith("\\\\")) return false;
        java.util.regex.Matcher scheme = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):").matcher(link);
        if (!scheme.find()) return true;
        String name = scheme.group(1).toLowerCase(java.util.Locale.ROOT);
        return "http".equals(name) || "https".equals(name) || "mailto".equals(name);
    }
}
