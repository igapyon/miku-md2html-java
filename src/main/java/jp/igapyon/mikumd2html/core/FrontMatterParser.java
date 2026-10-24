package jp.igapyon.mikumd2html.core;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;

import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.regex.Pattern;

/** Parses the Node runtime's YAML 1.2 metadata front matter without exposing YAML objects. */
public final class FrontMatterParser {
    private static final Pattern LANG = Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");
    private static final int MAX_ALIASES = 50;

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
            frontMatterError(diagnostics);
            return new FrontMatter(null, null, null, "", 0, false);
        }

        String yaml = join(lines, 1, closing);
        String title = null;
        String description = null;
        String lang = null;
        boolean valid = true;
        try {
            LoadSettings settings = LoadSettings.builder()
                    .setSchema(new CoreSchema())
                    .setAllowDuplicateKeys(false)
                    .setAllowRecursiveKeys(false)
                    .setMaxAliasesForCollections(MAX_ALIASES)
                    .setCodePointLimit(1_048_576)
                    .build();
            Iterator<Object> documents = new Load(settings).loadAllFromString(yaml).iterator();
            Object document = documents.hasNext() ? documents.next() : null;
            if (documents.hasNext()) throw new IllegalArgumentException("Front matter must contain one YAML document.");
            if (!(document instanceof Map<?, ?>)) throw new IllegalArgumentException("The front matter root must be a mapping.");
            Map<?, ?> values = (Map<?, ?>) document;
            title = stringField(values, "title", diagnostics);
            description = stringField(values, "description", diagnostics);
            Object language = values.get("lang");
            if (language != null || values.containsKey("lang")) {
                if (!(language instanceof String)) {
                    metadataTypeWarning(diagnostics, "lang");
                } else {
                    String value = ((String) language).trim();
                    if (!value.isEmpty() && !LANG.matcher(value).matches()) metadataTypeWarning(diagnostics, "lang");
                    else if (!value.isEmpty()) lang = value;
                }
            }
        } catch (RuntimeException ex) {
            valid = false;
            frontMatterError(diagnostics);
        }

        String body = join(lines, closing + 1, lines.length);
        return new FrontMatter(title, description, lang, body, closing + 1, valid);
    }

    private static String stringField(Map<?, ?> values, String field, List<Diagnostic> diagnostics) {
        Object value = values.get(field);
        if (value == null) {
            if (values.containsKey(field)) metadataTypeWarning(diagnostics, field);
            return null;
        }
        if (!(value instanceof String)) {
            metadataTypeWarning(diagnostics, field);
            return null;
        }
        String normalized = normalizeText((String) value);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeText(String value) {
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static void metadataTypeWarning(List<Diagnostic> diagnostics, String field) {
        Diagnostics.add(diagnostics, "warning", "W_METADATA_TYPE",
                "Front matter " + field + ("lang".equals(field)
                        ? " is invalid; using the default language."
                        : " must be a string; ignoring it."),
                "markdown", new SourcePosition(2, 1), java.util.Collections.<String, Object>singletonMap("field", field));
    }

    private static void frontMatterError(List<Diagnostic> diagnostics) {
        Diagnostics.add(diagnostics, "error", "E_FRONT_MATTER",
                "Invalid YAML front matter, mapping shape, duplicate key, or alias limit.",
                "markdown", new SourcePosition(1, 1));
    }

    private static String join(String[] lines, int start, int end) {
        StringBuilder value = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) value.append('\n');
            value.append(lines[i]);
        }
        return value.toString();
    }
}
