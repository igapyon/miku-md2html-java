package jp.igapyon.mikumd2html.core;

public final class FrontMatter {
    private final String title;
    private final String description;
    private final String lang;
    private final String body;
    private final int lineOffset;
    private final boolean valid;

    public FrontMatter(String title, String description, String lang, String body, int lineOffset, boolean valid) {
        this.title = title;
        this.description = description;
        this.lang = lang;
        this.body = body;
        this.lineOffset = lineOffset;
        this.valid = valid;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLang() { return lang; }
    public String getBody() { return body; }
    public int getLineOffset() { return lineOffset; }
    public boolean isValid() { return valid; }
}
