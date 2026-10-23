package jp.igapyon.mikumd2html.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class FrontMatterParser {
    private static final Pattern KEY_VALUE = Pattern.compile("^([A-Za-z][A-Za-z0-9_-]*):(?:[ \\t]*(.*))?$");
    private static final Pattern LANG = Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");

    public FrontMatter parse(String markdown, List<Diagnostic> diagnostics) {
        int firstBreak = markdown.indexOf('\n');
        String firstLine = firstBreak < 0 ? markdown : markdown.substring(0, firstBreak);
        if (!"---".equals(firstLine)) return new FrontMatter(null, null, null, markdown, 0, true);

        String[] lines = markdown.split("\n", -1);
        int closing = -1;
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i]) || "...".equals(lines[i])) { closing = i; break; }
        }
        if (closing < 0) {
            Diagnostics.add(diagnostics, "error", "E_FRONT_MATTER", "Front matter is not closed.", "markdown", new SourcePosition(1, 1));
            return new FrontMatter(null, null, null, "", 0, false);
        }

        String title = null;
        String description = null;
        String lang = null;
        Set<String> seen = new HashSet<String>();
        boolean valid = true;
        for (int i = 1; i < closing; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            java.util.regex.Matcher matcher = KEY_VALUE.matcher(line);
            if (!matcher.matches()) {
                valid = false;
                break;
            }
            String key = matcher.group(1);
            String rawValue = matcher.group(2) == null ? "" : matcher.group(2).trim();
            if (!seen.add(key)) {
                valid = false;
                break;
            }
            if (!"title".equals(key) && !"description".equals(key) && !"lang".equals(key)) continue;
            String value;
            try { value = scalar(rawValue); }
            catch (IllegalArgumentException ex) { valid = false; break; }
            if (isNonStringScalar(rawValue)) {
                Diagnostics.add(diagnostics, "warning", "W_METADATA_TYPE", "Front matter " + key + " must be a string; ignoring it.",
                        "markdown", new SourcePosition(i + 1, 1));
                continue;
            }
            value = normalizeText(value);
            if ("title".equals(key)) title = value;
            else if ("description".equals(key)) description = value;
            else if ("lang".equals(key)) {
                if (!value.isEmpty() && !LANG.matcher(value).matches()) {
                    Diagnostics.add(diagnostics, "warning", "W_METADATA_TYPE", "Front matter lang is invalid; using the default language.",
                            "markdown", new SourcePosition(i + 1, 1));
                } else if (!value.isEmpty()) lang = value;
            }
        }
        if (!valid) {
            Diagnostics.add(diagnostics, "error", "E_FRONT_MATTER", "Invalid YAML front matter, mapping shape, duplicate key, or unsupported YAML syntax.",
                    "markdown", new SourcePosition(1, 1));
        }
        StringBuilder body = new StringBuilder();
        for (int i = closing + 1; i < lines.length; i++) {
            if (i > closing + 1) body.append('\n');
            body.append(lines[i]);
        }
        return new FrontMatter(title, description, lang, body.toString(), closing + 1, valid);
    }

    private static String scalar(String value) {
        if (value.isEmpty()) return "";
        if (value.charAt(0) == '\'' && value.length() >= 2 && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.charAt(0) == '"' && value.length() >= 2 && value.charAt(value.length() - 1) == '"') {
            StringBuilder result = new StringBuilder();
            for (int i = 1; i < value.length() - 1; i++) {
                char c = value.charAt(i);
                if (c == '\\') {
                    if (++i >= value.length() - 1) throw new IllegalArgumentException("bad escape");
                    char escaped = value.charAt(i);
                    if (escaped == 'n') result.append('\n');
                    else if (escaped == 'r') result.append('\r');
                    else if (escaped == 't') result.append('\t');
                    else if (escaped == '"' || escaped == '\\' || escaped == '/') result.append(escaped);
                    else throw new IllegalArgumentException("unsupported escape");
                } else result.append(c);
            }
            return result.toString();
        }
        if (value.startsWith("[") || value.startsWith("{") || value.startsWith("&") || value.startsWith("*")
                || value.startsWith("!") || value.startsWith("|") || value.startsWith(">")) {
            throw new IllegalArgumentException("unsupported YAML scalar");
        }
        return value;
    }

    private static boolean isNonStringScalar(String raw) {
        if (raw.isEmpty() || raw.startsWith("'") || raw.startsWith("\"")) return false;
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        return "true".equals(lower) || "false".equals(lower) || "null".equals(lower) || "~".equals(lower)
                || raw.matches("[-+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");
    }

    private static String normalizeText(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
