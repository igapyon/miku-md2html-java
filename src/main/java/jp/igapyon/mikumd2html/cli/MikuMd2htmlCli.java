package jp.igapyon.mikumd2html.cli;

import java.io.PrintStream;
import jp.igapyon.mikumd2html.core.MikuMd2htmlMetadata;

/** Thin executable boundary; argument parsing and conversion remain independently testable. */
public final class MikuMd2htmlCli {
    private static final String HELP = MikuMd2htmlMetadata.PRODUCT_NAME + " " + MikuMd2htmlMetadata.VERSION + "\n"
            + "\nConvert one local Markdown file and its local images into a self-contained HTML file.\n"
            + "\nUsage:\n"
            + "  java -jar miku-md2html-java-" + MikuMd2htmlMetadata.VERSION + ".jar <input.md> --out <output.html> [options]\n"
            + "  java -jar miku-md2html-java-" + MikuMd2htmlMetadata.VERSION + ".jar --help\n"
            + "  java -jar miku-md2html-java-" + MikuMd2htmlMetadata.VERSION + ".jar --version\n"
            + "\nOptions:\n"
            + "  --out <file>             Required .html destination.\n"
            + "  --title <text>           Override the document title.\n"
            + "  --lang <tag>             Set the document language (default: ja).\n"
            + "  --asset-root <directory> Allow local image files under this directory.\n"
            + "  --no-toc                 Omit the generated table of contents.\n"
            + "  --max-output-mib <n>     HTML size limit from 1 through 1024 MiB (default: 100).\n"
            + "  --report-json <file>     Write a versioned JSON result report.\n"
            + "  --force                  Replace existing regular output/report files.\n"
            + "  --help                   Show this help.\n"
            + "  --version                Show the full package version.\n"
            + "\nRemote images are rejected. Runtime dependencies are limited to the Java standard library.\n"
            + "Success writes a one-line summary to stdout; diagnostics go to stderr. Exit codes: 0 success, 1 conversion/I/O failure, 2 usage error, 3 internal error.\n"
            + "\nExample:\n"
            + "  java -jar miku-md2html-java-" + MikuMd2htmlMetadata.VERSION + ".jar ./guide.md --out ./out/guide.html\n";

    private MikuMd2htmlCli() { }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && "--help".equals(args[0])) { out.print(HELP); return 0; }
        if (args.length == 1 && "--version".equals(args[0])) { out.println(MikuMd2htmlMetadata.PRODUCT_NAME + " " + MikuMd2htmlMetadata.VERSION); return 0; }
        final CliOptions options;
        try { options = CliArguments.parse(args); }
        catch (CliUsageException ex) {
            err.println("error E_USAGE " + ex.getMessage());
            err.println(HELP.split("\n", 8)[0]);
            err.println("Usage: java -jar miku-md2html-java-" + MikuMd2htmlMetadata.VERSION + ".jar <input.md> --out <output.html> [options]");
            return 2;
        } catch (RuntimeException ex) {
            err.println("error E_USAGE Invalid argument path or value.");
            return 2;
        }
        try { return FileConversion.run(options, out, err); }
        catch (Throwable ex) { err.println("error E_INTERNAL Unexpected internal error."); return 3; }
    }

    public static void main(String[] args) {
        int exit = run(args, System.out, System.err);
        if (exit != 0) System.exit(exit);
    }
}
