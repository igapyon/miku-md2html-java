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
import jp.igapyon.mikumd2html.core.ConversionStats;
import jp.igapyon.mikumd2html.core.Diagnostic;
import jp.igapyon.mikumd2html.core.MikuMd2htmlCore;
import jp.igapyon.mikumd2html.core.MikuMd2htmlMetadata;
import jp.igapyon.mikumd2html.core.SourcePosition;

/** File and publication adapter; the conversion pipeline remains usable without this CLI. */
public final class FileConversion {
    interface OutputFileOperations {
        Path stage(Path target, byte[] bytes, String code);
        void commit(Path staged, Path target, boolean force, String code);
        void cleanup(Path staged);
    }

    private static final OutputFileOperations FILE_OPERATIONS = new OutputFileOperations() {
        @Override public Path stage(Path target, byte[] bytes, String code) { return stageFile(target, bytes, code); }
        @Override public void commit(Path staged, Path target, boolean force, String code) { commitStage(staged, target, force, code); }
        @Override public void cleanup(Path staged) { cleanupStage(staged); }
    };

    private FileConversion() { }

    public static int run(CliOptions cli, java.io.PrintStream out, java.io.PrintStream err) {
        return run(cli, out, err, FILE_OPERATIONS);
    }

    static int run(CliOptions cli, java.io.PrintStream out, java.io.PrintStream err, OutputFileOperations fileOperations) {
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        ConvertResult result = null;
        Integer inputBytes = null;
        Path inputReal = null;
        Path outputTarget = null;
        Path reportTarget = null;
        Path outputStage = null;
        Path reportStage = null;
        LocalAssetResolver assets = null;
        boolean reportWritable = false;
        try {
            outputTarget = canonicalTarget(cli.output);
            validateTarget(outputTarget, cli.force, "HTML");
            if (cli.reportJson != null) {
                reportTarget = canonicalTarget(cli.reportJson);
                validateTarget(reportTarget, cli.force, "JSON report");
                if (samePath(outputTarget, reportTarget)) throw new AssetAccessException("E_OUTPUT_COLLISION", "HTML and report paths must be different files.");
                reportWritable = true;
            }
            inputReal = cli.input.toAbsolutePath().normalize().toRealPath();
            BasicFileAttributes inputAttrs = Files.readAttributes(inputReal, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!inputAttrs.isRegularFile()) throw new AssetAccessException("E_INPUT_READ", "Input is not a regular file: " + cli.input);
            if (samePath(inputReal, outputTarget)) throw new AssetAccessException("E_OUTPUT_COLLISION", "HTML output must not overwrite the Markdown input.");
            if (reportTarget != null && samePath(inputReal, reportTarget)) throw new AssetAccessException("E_OUTPUT_COLLISION", "JSON report must not overwrite the Markdown input.");
            byte[] input = readBounded(inputReal, cli.maxOutputBytes);
            inputBytes = input.length;
            String markdown = decodeUtf8(input);
            assets = LocalAssetResolver.create(inputReal, cli.assetRoot, outputTarget);
            ConvertOptions options = new ConvertOptions();
            options.setTitle(cli.title);
            options.setLang(cli.lang);
            options.setToc(!cli.noToc);
            options.setMaxOutputBytes(cli.maxOutputBytes);
            options.setAssets(assets);
            options.setImageMode(cli.imageMode);
            result = MikuMd2htmlCore.convertMarkdown(markdown, cli.input.getFileName().toString(), options);
            diagnostics.addAll(result.getDiagnostics());
            rejectAssetCollisions(assets, outputTarget, reportTarget);
            if (!result.isSuccess()) {
                printDiagnostics(diagnostics, err);
                if (reportTarget != null) writeReport(reportTarget, reportJson(cli, false, 1, inputBytes, result, diagnostics, false), cli.force, err);
                return 1;
            }
            outputStage = fileOperations.stage(outputTarget, result.getHtml().getBytes(StandardCharsets.UTF_8), "E_OUTPUT_WRITE");
            if (reportTarget != null) {
                reportStage = fileOperations.stage(reportTarget,
                        reportJson(cli, true, 0, inputBytes, result, diagnostics, true).getBytes(StandardCharsets.UTF_8),
                        "E_REPORT_WRITE");
            }
            fileOperations.commit(outputStage, outputTarget, cli.force, "E_OUTPUT_WRITE");
            if (reportStage != null) {
                try {
                    fileOperations.commit(reportStage, reportTarget, cli.force, "E_REPORT_WRITE");
                } catch (AssetAccessException ex) {
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
        } finally {
            fileOperations.cleanup(outputStage);
            fileOperations.cleanup(reportStage);
        }
        printDiagnostics(diagnostics, err);
        int failureExitCode = hasDiagnostic(diagnostics, "E_INTERNAL") ? 3 : 1;
        if (reportWritable && reportTarget != null && !hasDiagnostic(diagnostics, "E_OUTPUT_COLLISION"))
            writeReport(reportTarget, reportJson(cli, false, failureExitCode, inputBytes, result, diagnostics, false), cli.force, err);
        return failureExitCode;
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
        Path temp = stageFile(target, bytes, "E_REPORT_WRITE");
        try {
            commitStage(temp, target, force, "E_REPORT_WRITE");
        } finally { cleanupStage(temp); }
    }

    private static Path stageFile(Path target, byte[] bytes, String code) {
        Path temp = null;
        try {
            temp = Files.createTempFile(target.getParent(), ".miku-md2html-", ".tmp");
            Files.write(temp, bytes);
            return temp;
        } catch (IOException ex) {
            cleanupStage(temp);
            String label = "E_OUTPUT_WRITE".equals(code) ? "HTML output" : "JSON report";
            throw new AssetAccessException(code, label + " could not be staged: " + safeMessage(ex), ex);
        }
    }

    private static void commitStage(Path temp, Path target, boolean force, String code) {
        try {
            if (force) {
                moveReplacing(temp, target);
                return;
            }
            try {
                Files.createLink(target, temp);
            } catch (UnsupportedOperationException ex) {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw outputExists(code, ex);
                Files.move(temp, target);
            } catch (java.nio.file.FileAlreadyExistsException ex) {
                throw outputExists(code, ex);
            } catch (IOException linkFailure) {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw outputExists(code, linkFailure);
                Files.move(temp, target);
            }
        } catch (AssetAccessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AssetAccessException(code, "Staged output could not be committed: " + safeMessage(ex), ex);
        }
    }

    private static AssetAccessException outputExists(String code, Exception cause) {
        String message = "E_REPORT_WRITE".equals(code)
                ? "JSON report already exists; use --force to replace it."
                : "Output already exists; use --force to replace it.";
        return new AssetAccessException("E_OUTPUT_EXISTS", message, cause);
    }

    private static void cleanupStage(Path temp) {
        if (temp == null) return;
        try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
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

    private static String reportJson(CliOptions cli, boolean success, int exitCode, Integer inputBytes, ConvertResult result,
            List<Diagnostic> diagnostics, boolean outputWritten) {
        StringBuilder json = new StringBuilder("{\n  \"schemaVersion\": \"2\",\n  \"tool\": {\"name\": \"")
                .append(MikuMd2htmlMetadata.PRODUCT_NAME).append("\", \"version\": \"").append(MikuMd2htmlMetadata.VERSION)
                .append("\"},\n  \"success\": ").append(success).append(",\n  \"exitCode\": ").append(exitCode)
                .append(",\n  \"input\": {\"path\": \"").append(jsonEscape(cli.input.toString())).append('"');
        if (inputBytes != null) json.append(", \"bytes\": ").append(inputBytes.intValue());
        json.append("},\n  \"output\": {\"path\": \"").append(jsonEscape(cli.output.toString()))
                .append("\", \"imageMode\": \"").append(jsonEscape(cli.imageMode)).append("\", \"written\": ").append(outputWritten);
        if (outputWritten && result != null) json.append(", \"bytes\": ").append(result.getStats().getHtmlBytes());
        json.append("},\n  \"metadata\": {\"title\": \"").append(jsonEscape(result == null ? (cli.title == null ? "Untitled" : cli.title) : result.getTitle()))
                .append("\", \"lang\": \"").append(jsonEscape(result == null ? (cli.lang == null ? "ja" : cli.lang) : result.getLang())).append('"');
        String description = result == null ? null : result.getDescription();
        if (description != null) json.append(", \"description\": \"").append(jsonEscape(description)).append('"');
        json.append("},\n  \"stats\": {");
        if (result == null) {
            json.append("\"headingCount\":0,\"imageOccurrences\":0,\"uniqueAssets\":0,\"uniqueSourceAssetBytes\":0,\"embeddedPayloadBytes\":0,\"embeddedImageOccurrences\":0,\"referencedImageOccurrences\":0,\"omittedImageOccurrences\":0");
        } else {
            ConversionStats stats = result.getStats();
            json.append("\"headingCount\":").append(stats.getHeadingCount()).append(",\"imageOccurrences\":").append(stats.getImageOccurrences())
                    .append(",\"uniqueAssets\":").append(stats.getUniqueAssets()).append(",\"uniqueSourceAssetBytes\":").append(stats.getUniqueSourceAssetBytes())
                    .append(",\"embeddedPayloadBytes\":").append(stats.getEmbeddedPayloadBytes())
                    .append(",\"embeddedImageOccurrences\":").append(stats.getEmbeddedImageOccurrences())
                    .append(",\"referencedImageOccurrences\":").append(stats.getReferencedImageOccurrences())
                    .append(",\"omittedImageOccurrences\":").append(stats.getOmittedImageOccurrences());
            if (result.isSuccess()) json.append(",\"htmlBytes\":").append(stats.getHtmlBytes());
        }
        json.append("},\n  \"diagnostics\": [");
        List<Diagnostic> orderedDiagnostics = jp.igapyon.mikumd2html.core.Diagnostics.stable(diagnostics);
        for (int i = 0; i < orderedDiagnostics.size(); i++) {
            Diagnostic d = orderedDiagnostics.get(i); if (i > 0) json.append(',');
            json.append("\n    {\"severity\":\"").append(jsonEscape(d.getSeverity())).append("\",\"code\":\"").append(jsonEscape(d.getCode()))
                    .append("\",\"message\":\"").append(jsonEscape(d.getMessage())).append('"');
            if (d.getLabel() != null) {
                json.append(",\"source\":{\"label\":\"").append(jsonEscape(d.getLabel())).append('"');
                appendSourcePosition(json, "start", d.getStart());
                appendSourcePosition(json, "end", d.getEnd());
                json.append('}');
            }
            appendDetails(json, d.getDetails());
            json.append('}');
        }
        return json.append("\n  ]\n}\n").toString();
    }

    private static void appendSourcePosition(StringBuilder json, String key, SourcePosition position) {
        if (position != null) json.append(",\"").append(key).append("\":{\"line\":").append(position.getLine())
                .append(",\"column\":").append(position.getColumn()).append('}');
    }

    private static void appendDetails(StringBuilder json, java.util.Map<String, Object> details) {
        if (details.isEmpty()) return;
        int start = json.length();
        json.append(",\"details\":{");
        boolean first = true;
        for (java.util.Map.Entry<String, Object> entry : details.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) continue;
            if (!first) json.append(',');
            first = false;
            json.append('"').append(jsonEscape(entry.getKey())).append("\":");
            if (value instanceof String) json.append('"').append(jsonEscape((String) value)).append('"');
            else json.append(value.toString());
        }
        if (first) json.setLength(start);
        else json.append('}');
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
