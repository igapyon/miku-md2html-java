package jp.igapyon.mikumd2html.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarkdownDocument {
    private final String articleHtml;
    private final List<DocumentStructure.Heading> headings;

    public MarkdownDocument(String articleHtml, List<DocumentStructure.Heading> headings) {
        this.articleHtml = articleHtml;
        this.headings = Collections.unmodifiableList(new ArrayList<DocumentStructure.Heading>(headings));
    }

    public String getArticleHtml() { return articleHtml; }
    public List<DocumentStructure.Heading> getHeadings() { return headings; }
}
