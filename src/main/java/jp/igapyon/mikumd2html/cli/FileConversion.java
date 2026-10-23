package jp.igapyon.mikumd2html.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import jp.igapyon.mikumd2html.adapters.LocalAssetResolver;
import jp.igapyon.mikumd2html.core.AssetAccessException;
import jp.igapyon.mikumd2html.core.ConvertOptions;
import jp.igapyon.mikumd2html.core.ConvertResult;
import jp.igapyon.mikumd2html.core.Diagnostic;
import jp.igapyon.mikumd2html.core.MikuMd2htmlCore;
import jp.igapyon.mikumd2html.core.MikuMd2htmlMetadata;
import jp.igapyon.mikumd2html.core.SourcePosition;

/** File and publication adapter; the conversion pipeline remains usable without this CLI. */
public final class FileConversion {
    private FileConversion() { }

    public static int run(CliOptions cli, java.io.PrintStream out, java.io.PrintStream err) {
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        ConvertResult result = null;
        int inputBytes = 0;
        Path inputReal = null;
        Path outputTarget = null;
        Path reportTarget = null;
        LocalAssetResolver assets = null;
        boolean reportWritable = false;
        try {
            inputReal = cli.input.toAbsolutePath().normalize().toRealPath();
            BasicFileAttributes inputAttrs = Files.readAttributes(inputReal, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!inputAttrs.isRegularFile()) throw new AssetAccessException("E_INPUT_READ", "Input is not a regular file: " + cli.input);
            outputTarget = canonicalTarget(cli.output);
            validateTarget(outputTarget, cli.force, "HTML");
            if (samePath(inputReal, outputTarget)) throw new AssetAccessException("E_OUTPUT_COLLISION", "HTML output must not overwrite the Markdown input.");
            if (cli.reportJson != null) {
                reportTarget = canonicalTarget(cli.reportJson);
                validateTarget(reportTarget, cli.force, "JSON report");
                if (samePath(inputReal, reportTarget)) throw new AssetAccessException("E_OUTPUT_COLLISION", "JSON report must not overwrite the Markdown input.");
                if (samePath(outputTarget, reportTarget)) throw new AssetAccessException("E_OUTPUT_COLLISION", "HTML and report paths must be different files.");
                reportWritable = true;
            }
            byte[] input = readBounded(inputReal, cli.maxOutputBytes);
            inputBytes = input.length;
            String markdown = decodeUtf8(input);
            assets = LocalAssetResolver.create(inputReal, cli.assetRoot);
            ConvertOptions options = new ConvertOptions();
            options.setTitle(cli.title);
            options.setLang(cli.lang);
            options.setToc(!cli.noToc);
            options.setMaxOutputBytes(cli.maxOutputBytes);
            options.setAssets(assets);
            result = MikuMd2htmlCore.convertMarkdown(markdown, cli.input.getFileName().toString(), options);
            diagnostics.addAll(result.getDiagnostics());
            rejectAssetCollisions(assets, outputTarget, reportTarget);
            if (!result.isSuccess()) {
                printDiagnostics(diagnostics, err);
                if (reportTarget != null) writeReport(reportTarget, reportJson(cli, false, 1, inputBytes, result, diagnostics, false), cli.force, err);
                return 1;
            }
            try {
                writeAtomic(outputTarget, result.getHtml().getBytes(StandardCharsets.UTF_8), cli.force);
            } catch (AssetAccessException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new AssetAccessException("E_OUTPUT_WRITE", "HTML output could not be saved: " + safeMessage(ex), ex);
            }
            if (reportTarget != null) {
                try {
                    writeAtomic(reportTarget, reportJson(cli, true, 0, inputBytes, result, diagnostics, true).getBytes(StandardCharsets.UTF_8), cli.force);
                } catch (Exception ex) {
                    err.println("error E_REPORT_WRITE HTML was saved to " + cli.output + "; the JSON report could not be saved: " + safeMessage(ex));
                    printDiagnostics(diagnostics, err);
                    return 1;
                }
            }
            printDiagnostics(diagnostics, err);
            out.println("Converted " + cli.input + " -> " + cli.output + " (" + result.getStats().getHtmlBytes() + " bytes, "
                    + result.getStats().getHeadingCount() + " headings, " + result.getStats().getImageOccurrences() + " images).");
            return 0;
        } catch (AssetAccessException ex) {
            diagnostics.add(new Diagnostic("error", ex.getCode(), ex.getMessage(), null, null, null));
        } catch (CharacterCodingException ex) {
            diagnostics.add(new Diagnostic("error", "E_INPUT_ENCODING", "Input is not valid UTF-8.", cli.input.toString(), null, null));
        } catch (IOException ex) {
            diagnostics.add(new Diagnostic("error", "E_INPUT_READ", "Input or output file could not be read or written: " + safeMessage(ex), cli.input.toString(), null, null));
        } catch (Exception ex) {
            diagnostics.add(new Diagnostic("error", "E_INTERNAL", "Unexpected internal error.", null, null, null));
            printDiagnostics(diagnostics, err);
            return 3;
        }
        printDiagnostics(diagnostics, err);
        if (reportWritable && reportTarget != null && !hasDiagnostic(diagnostics, "E_OUTPUT_COLLISION"))
            writeReport(reportTarget, reportJson(cli, false, 1, inputBytes, result, diagnostics, false), cli.force, err);
        return 1;
    }

    private static byte[] readBounded(Path path, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[32768]; int count; long total = 0;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > maxBytes) throw new AssetAccessException("E_INPUT_TOO_LARGE", "Markdown input exceeds the configured output limit.");
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Path canonicalTarget(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("Output directory does not exist: " + parent);
        return parent.toRealPath().resolve(absolute.getFileName());
    }

    private static void validateTarget(Path target, boolean force, String label) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile()) throw new AssetAccessException("E_OUTPUT_WRITE", label + " destination is not a regular file.");
        if (!force) throw new AssetAccessException("E_OUTPUT_EXISTS", label + " already exists; use --force to replace it.");
    }

    private static boolean samePath(Path left, Path right) throws IOException {
        return left.equals(right) || (Files.exists(left) && Files.exists(right) && Files.isSameFile(left, right));
    }

    private static void rejectAssetCollisions(LocalAssetResolver assets, Path output, Path report) throws IOException {
        for (Path referenced : assets.getReferencedPaths()) {
            Path asset = Files.exists(referenced) ? referenced.toRealPath() : referenced.toAbsolutePath().normalize();
            if (samePath(asset, output) || (report != null && samePath(asset, report))) {
                throw new AssetAccessException("E_OUTPUT_COLLISION", "An output path refers to a referenced image asset.");
            }
        }
    }

    private static void writeReport(Path target, String report, boolean force, java.io.PrintStream err) {
        try { writeAtomic(target, report.getBytes(StandardCharsets.UTF_8), force); }
        catch (Exception ex) { err.println("error E_REPORT_WRITE JSON report could not be saved: " + safeMessage(ex)); }
    }

    private static void writeAtomic(Path target, byte[] bytes, boolean force) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".miku-md2html-", ".tmp");
        try {
            Files.write(temp, bytes);
            if (force) moveReplacing(temp, target);
            else {
                try {
                    Files.createLink(target, temp);
                    Files.delete(temp);
                } catch (UnsupportedOperationException ex) {
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new AssetAccessException("E_OUTPUT_EXISTS", "Output already exists.", ex);
                    Files.move(temp, target);
                } catch (IOException ex) {
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new AssetAccessException("E_OUTPUT_EXISTS", "Output already exists; use --force to replace it.", ex);
                    Files.move(temp, target);
                }
            }
        } finally { Files.deleteIfExists(temp); }
    }

    private static void moveReplacing(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void printDiagnostics(List<Diagnostic> diagnostics, java.io.PrintStream err) {
        for (Diagnostic d : diagnostics) {
            StringBuilder line = new StringBuilder(d.getSeverity()).append(' ').append(d.getCode()).append(' ');
            if (d.getLabel() != null) {
                line.append(d.getLabel());
                SourcePosition p = d.getStart();
                if (p != null) line.append(':').append(p.getLine()).append(':').append(p.getColumn());
                line.append(": ");
            }
            line.append(d.getMessage()); err.println(line.toString());
        }
    }

    private static String reportJson(CliOptions cli, boolean success, int exitCode, int inputBytes, ConvertResult result,
            List<Diagnostic> diagnostics, boolean outputWritten) {
        StringBuilder json = new StringBuilder("{\n  \"schemaVersion\": \"1\",\n  \"tool\": {\"name\": \"")
                .append(MikuMd2htmlMetadata.PRODUCT_NAME).append("\", \"version\": \"").append(MikuMd2htmlMetadata.VERSION)
                .append("\"},\n  \"success\": ").append(success).append(",\n  \"exitCode\": ").append(exitCode)
                .append(",\n  \"input\": {\"path\": \"").append(jsonEscape(cli.input.toString())).append("\", \"bytes\": ").append(inputBytes)
                .append("},\n  \"output\": {\"path\": \"").append(jsonEscape(cli.output.toString())).append("\", \"written\": ").append(outputWritten);
        if (outputWritten && result != null) json.append(", \"bytes\": ").append(result.getStats().getHtmlBytes());
        json.append("},\n  \"metadata\": {\"title\": \"").append(jsonEscape(result == null ? (cli.title == null ? "Untitled" : cli.title) : result.getTitle()))
                .append("\", \"lang\": \"").append(jsonEscape(result == null ? (cli.lang == null ? "ja" : cli.lang) : result.getLang())).append("\"},\n  \"stats\": {");
        if (result == null) json.append("\"headingCount\":0,\"imageOccurrences\":0,\"uniqueAssets\":0,\"uniqueSourceAssetBytes\":0,\"embeddedPayloadBytes\":0,\"htmlBytes\":0");
        else json.append("\"headingCount\":").append(result.getStats().getHeadingCount()).append(",\"imageOccurrences\":").append(result.getStats().getImageOccurrences())
                .append(",\"uniqueAssets\":").append(result.getStats().getUniqueAssets()).append(",\"uniqueSourceAssetBytes\":").append(result.getStats().getUniqueSourceAssetBytes())
                .append(",\"embeddedPayloadBytes\":").append(result.getStats().getEmbeddedPayloadBytes()).append(",\"htmlBytes\":").append(result.getStats().getHtmlBytes());
        json.append("},\n  \"diagnostics\": [");
        for (int i = 0; i < diagnostics.size(); i++) {
            Diagnostic d = diagnostics.get(i); if (i > 0) json.append(',');
            json.append("\n    {\"severity\":\"").append(jsonEscape(d.getSeverity())).append("\",\"code\":\"").append(jsonEscape(d.getCode()))
                    .append("\",\"message\":\"").append(jsonEscape(d.getMessage())).append('"');
            if (d.getLabel() != null) json.append(",\"source\":{\"label\":\"").append(jsonEscape(d.getLabel())).append('"')
                    .append(d.getStart() == null ? "}" : ",\"start\":{\"line\":" + d.getStart().getLine() + ",\"column\":" + d.getStart().getColumn() + "}}");
            json.append('}');
        }
        return json.append("\n  ]\n}\n").toString();
    }

    private static String jsonEscape(String value) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') output.append('\\').append(c);
            else if (c == '\n') output.append("\\n");
            else if (c == '\r') output.append("\\r");
            else if (c == '\t') output.append("\\t");
            else if (c < 0x20) output.append(String.format("\\u%04x", (int) c));
            else output.append(c);
        }
        return output.toString();
    }

    private static String safeMessage(Exception ex) { return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }
    private static boolean hasDiagnostic(List<Diagnostic> diagnostics, String code) {
        for (Diagnostic diagnostic : diagnostics) if (code.equals(diagnostic.getCode())) return true;
        return false;
    }
}
