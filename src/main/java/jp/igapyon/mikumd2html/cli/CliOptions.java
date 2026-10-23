package jp.igapyon.mikumd2html.cli;

import java.nio.file.Path;

public final class CliOptions {
    public final Path input;
    public final Path output;
    public final String title;
    public final String lang;
    public final Path assetRoot;
    public final boolean noToc;
    public final long maxOutputBytes;
    public final Path reportJson;
    public final boolean force;

    CliOptions(Path input, Path output, String title, String lang, Path assetRoot, boolean noToc,
            long maxOutputBytes, Path reportJson, boolean force) {
        this.input = input; this.output = output; this.title = title; this.lang = lang; this.assetRoot = assetRoot;
        this.noToc = noToc; this.maxOutputBytes = maxOutputBytes; this.reportJson = reportJson; this.force = force;
    }
}
