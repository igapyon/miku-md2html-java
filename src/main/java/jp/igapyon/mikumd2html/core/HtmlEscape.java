package jp.igapyon.mikumd2html.core;

public final class HtmlEscape {
    private HtmlEscape() { }

    public static String text(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String attribute(String value) {
        return text(value).replace("\"", "&quot;").replace("'", "&#39;");
    }
}
