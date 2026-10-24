package jp.igapyon.mikumd2html.core;

import java.util.List;

/** Keeps the public package boundary stable while delegating parsing to the internal Flexmark adapter. */
public final class MarkdownParser {
    private final List<Diagnostic> diagnostics;
    private final int lineOffset;
    private final ImageEmbeddingSession images;

    public MarkdownParser(List<Diagnostic> diagnostics, int lineOffset) {
        this(diagnostics, lineOffset, null);
    }

    public MarkdownParser(List<Diagnostic> diagnostics, int lineOffset, ImageEmbeddingSession images) {
        this.diagnostics = diagnostics;
        this.lineOffset = lineOffset;
        this.images = images;
    }

    public MarkdownDocument parse(String markdown) {
        return new jp.igapyon.mikumd2html.core.markdown.FlexmarkAdapter(diagnostics, lineOffset, images).parse(markdown);
    }
}
