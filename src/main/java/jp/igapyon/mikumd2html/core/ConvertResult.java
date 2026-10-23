package jp.igapyon.mikumd2html.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConvertResult {
    private final boolean success;
    private final String html;
    private final String title;
    private final String lang;
    private final String description;
    private final List<Diagnostic> diagnostics;
    private final ConversionStats stats;

    public ConvertResult(boolean success, String html, String title, String lang, String description,
            List<Diagnostic> diagnostics, ConversionStats stats) {
        this.success = success;
        this.html = html;
        this.title = title;
        this.lang = lang;
        this.description = description;
        this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
        this.stats = stats;
    }

    public boolean isSuccess() { return success; }
    public String getHtml() { return html; }
    public String getTitle() { return title; }
    public String getLang() { return lang; }
    public String getDescription() { return description; }
    public List<Diagnostic> getDiagnostics() { return diagnostics; }
    public ConversionStats getStats() { return stats; }
}
