package jp.igapyon.mikumd2html.core;

/** Public conversion entry point, corresponding to the Node package's index module. */
public final class MikuMd2htmlCore {
    private MikuMd2htmlCore() { }

    public static ConvertResult convertMarkdown(String markdown, String inputName, ConvertOptions options) {
        return new MarkdownConverter().convert(markdown, inputName, options);
    }
}
