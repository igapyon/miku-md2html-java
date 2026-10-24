package jp.igapyon.mikumd2html.core;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DocumentStructure {
    private static final Pattern FRAGMENT = Pattern.compile("href=\"#([^\"]*)\"");

    public static final class Heading {
        private final int level;
        private final String text;
        private final String slug;
        private final String id;

        public Heading(int level, String text, String slug, String id) {
            this.level = level;
            this.text = text;
            this.slug = slug;
            this.id = id;
        }
        public int getLevel() { return level; }
        public String getText() { return text; }
        public String getSlug() { return slug; }
        public String getId() { return id; }
    }

    private DocumentStructure() { }

    public static String renderToc(List<Heading> headings, String lang) {
        if (headings.isEmpty()) return "";
        StringBuilder html = new StringBuilder("<details class=\"miku-md2html-toc\" open><summary>");
        html.append(lang.toLowerCase(Locale.ROOT).startsWith("ja") ? "目次" : "Contents");
        html.append("</summary><ol>\n");
        boolean first = true;
        for (Heading heading : headings) {
            if (!first) html.append('\n');
            first = false;
            html.append("<li data-level=\"").append(heading.getLevel()).append("\"><a href=\"#")
                    .append(HtmlEscape.attribute(heading.getId())).append("\">")
                    .append(HtmlEscape.text(heading.getText())).append("</a></li>");
        }
        return html.append("\n</ol></details>").toString();
    }

    public static String rewriteFragments(String html, List<Heading> headings, List<Diagnostic> diagnostics, int lineOffset) {
        Map<String, String> bySlug = new HashMap<String, String>();
        Set<String> ids = new HashSet<String>();
        for (Heading heading : headings) {
            ids.add(heading.getId());
            if (!bySlug.containsKey(heading.getSlug())) bySlug.put(heading.getSlug(), heading.getId());
        }
        Matcher generatedIds = Pattern.compile("\\bid=\"(miku-(?:fn|fnref)-[^\"]+)\"").matcher(html);
        while (generatedIds.find()) ids.add(generatedIds.group(1));
        Matcher matcher = FRAGMENT.matcher(html);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String decoded;
            try { decoded = URLDecoder.decode(matcher.group(1).replace("+", "%2B"), "UTF-8"); }
            catch (Exception ex) {
                Diagnostics.add(diagnostics, "warning", "W_ANCHOR_UNRESOLVED", "A fragment link has invalid percent encoding.",
                        "markdown", new SourcePosition(Math.max(1, lineOffset + 1), 1));
                continue;
            }
            if (ids.contains(decoded) || "miku-footnote-label".equals(decoded)) continue;
            String target = bySlug.get(Slug.slugify(decoded));
            if (target != null) matcher.appendReplacement(result, Matcher.quoteReplacement("href=\"#" + target + "\""));
            else {
                Diagnostics.add(diagnostics, "warning", "W_ANCHOR_UNRESOLVED", "A fragment link does not match a heading or footnote.",
                        "markdown", new SourcePosition(Math.max(1, lineOffset + 1), 1));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
