package jp.igapyon.mikumd2html.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Coordinates the same major conversion stages as the Node converter. */
public final class MarkdownConverter {
    private static final long DEFAULT_MAX_OUTPUT_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_OUTPUT_BYTES = 1024L * 1024L * 1024L;
    private static final long LARGE_OUTPUT_WARNING_BYTES = 20L * 1024L * 1024L;
    private static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");

    public ConvertResult convert(String source, String inputName, ConvertOptions suppliedOptions) {
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        ConvertOptions options = suppliedOptions == null ? new ConvertOptions() : suppliedOptions;
        String titleOverride = trimOrNull(options.getTitle());
        String langOverride = trimOrNull(options.getLang());
        long maxBytes = options.getMaxOutputBytes();
        if (source == null || inputName == null || inputName.trim().isEmpty() || inputName.matches(".*[\\\\/\\x00].*")) {
            Diagnostics.add(diagnostics, "error", "E_OPTIONS", "Request must include Markdown text and a plain input filename.", null, null);
            return failure("Untitled", "ja", null, diagnostics, emptyStats());
        }
        if (titleOverride != null && titleOverride.isEmpty()) {
            Diagnostics.add(diagnostics, "error", "E_OPTIONS", "Title must be a non-empty string when provided.", null, null);
        }
        if (langOverride != null && !LANGUAGE.matcher(langOverride).matches()) {
            Diagnostics.add(diagnostics, "error", "E_OPTIONS", "Language must be a supported ASCII language tag.", null, null);
        }
        if (maxBytes < 1 || maxBytes > MAX_OUTPUT_BYTES) {
            Diagnostics.add(diagnostics, "error", "E_OPTIONS", "maxOutputBytes must be an integer from 1 through 1073741824.", null, null);
            maxBytes = DEFAULT_MAX_OUTPUT_BYTES;
        }
        if (Diagnostics.hasErrors(diagnostics)) return failure("Untitled", "ja", null, diagnostics, emptyStats());

        String markdown = source.charAt(0) == '\ufeff' ? source.substring(1) : source;
        markdown = markdown.replace("\r\n", "\n").replace('\r', '\n');
        long inputBytes = markdown.getBytes(StandardCharsets.UTF_8).length;
        if (inputBytes > maxBytes) {
            Diagnostics.add(diagnostics, "error", "E_INPUT_TOO_LARGE", "Markdown input exceeds the configured output limit.", inputName, null);
            return failure("Untitled", langOverride == null ? "ja" : langOverride, null, diagnostics, emptyStats());
        }

        FrontMatter front = new FrontMatterParser().parse(markdown, diagnostics);
        String lang = langOverride != null ? langOverride : front.getLang() != null ? front.getLang() : "ja";
        String description = front.getDescription();
        ImageEmbeddingSession images = new ImageEmbeddingSession(options.getAssets(), diagnostics, maxBytes);
        MarkdownDocument document = new MarkdownParser(diagnostics, front.getLineOffset(), images).parse(front.getBody());
        List<DocumentStructure.Heading> headings = document.getHeadings();
        String firstH1 = null;
        for (DocumentStructure.Heading heading : headings) {
            if (heading.getLevel() == 1) { firstH1 = heading.getText().trim(); break; }
        }
        String fileTitle = inputName.replaceFirst("(?i)\\.(?:md|markdown)$", "").trim();
        String title = titleOverride != null ? titleOverride
                : front.getTitle() != null ? front.getTitle()
                : firstH1 != null && !firstH1.isEmpty() ? firstH1
                : !fileTitle.isEmpty() ? fileTitle : "Untitled";
        String article = DocumentStructure.rewriteFragments(document.getArticleHtml(), headings, diagnostics, front.getLineOffset());
        String toc = options.isToc() ? DocumentStructure.renderToc(headings, lang) : "";
        String html = new HtmlDocumentRenderer().render(article, toc, title, lang, description);
        long htmlBytes = html.getBytes(StandardCharsets.UTF_8).length;
        if (htmlBytes > maxBytes) {
            Diagnostics.add(diagnostics, "error", "E_OUTPUT_TOO_LARGE", "Generated HTML exceeds the configured output limit.", null, null);
            return failure(title, lang, description, diagnostics, stats(headings.size(), images, htmlBytes));
        }
        if (htmlBytes > LARGE_OUTPUT_WARNING_BYTES) {
            Diagnostics.add(diagnostics, "warning", "W_LARGE_OUTPUT", "Generated HTML exceeds 20 MiB.", null, null);
        }
        return new ConvertResult(!Diagnostics.hasErrors(diagnostics), Diagnostics.hasErrors(diagnostics) ? null : html,
                title, lang, description, Diagnostics.stable(diagnostics), stats(headings.size(), images, htmlBytes));
    }

    private static String trimOrNull(String value) { return value == null ? null : value.trim(); }
    private static ConversionStats emptyStats() { return new ConversionStats(0, 0, 0, 0L, 0L, 0L); }
    private static ConversionStats stats(int headings, ImageEmbeddingSession images, long htmlBytes) {
        return new ConversionStats(headings, images.getOccurrences(), images.getUniqueAssets(),
                images.getUniqueSourceAssetBytes(), images.getEmbeddedPayloadBytes(), htmlBytes);
    }
    private static ConvertResult failure(String title, String lang, String description, List<Diagnostic> diagnostics, ConversionStats stats) {
        return new ConvertResult(false, null, title, lang, description, Diagnostics.stable(diagnostics), stats);
    }
}
