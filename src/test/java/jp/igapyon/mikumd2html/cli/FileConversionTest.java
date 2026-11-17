package jp.igapyon.mikumd2html.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jp.igapyon.mikumd2html.core.AssetAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FileConversionTest {
    @TempDir Path temp;

    @Test void reportStageFailureDoesNotPublishHtmlAndWritesFailureReport() throws Exception {
        Path input = temp.resolve("article.md");
        Path output = temp.resolve("article.html");
        Path report = temp.resolve("report.json");
        Files.write(input, "# Article\n\nBody\n".getBytes(StandardCharsets.UTF_8));
        List<Path> staged = new ArrayList<Path>();
        FileConversion.OutputFileOperations operations = new FileConversion.OutputFileOperations() {
            @Override public Path stage(Path target, byte[] bytes, String code) {
                if ("E_REPORT_WRITE".equals(code)) throw new AssetAccessException(code, "simulated report staging failure");
                Path path = stageBytes(target, bytes);
                staged.add(path);
                return path;
            }
            @Override public void commit(Path stage, Path target, boolean force, String code) {
                throw new AssertionError("No output should be committed after report staging failed");
            }
            @Override public void cleanup(Path stage) { delete(stage); }
        };

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = FileConversion.run(options(input, output, report), stream(stdout), stream(stderr), operations);

        assertEquals(1, exit);
        assertFalse(Files.exists(output));
        assertTrue(Files.exists(report));
        assertTrue(new String(reportBytes(report), StandardCharsets.UTF_8).contains("\"success\": false"));
        assertTrue(new String(stderr.toByteArray(), StandardCharsets.UTF_8).contains("E_REPORT_WRITE"));
        assertEquals("", new String(stdout.toByteArray(), StandardCharsets.UTF_8));
        for (Path path : staged) assertFalse(Files.exists(path), "staging file should be cleaned");
    }

    @Test void reportCommitFailureKeepsHtmlButSuppressesSuccessSummary() throws Exception {
        Path input = temp.resolve("article.md");
        Path output = temp.resolve("article.html");
        Path report = temp.resolve("report.json");
        Files.write(input, "# Article\n\nBody\n".getBytes(StandardCharsets.UTF_8));
        FileConversion.OutputFileOperations operations = new FileConversion.OutputFileOperations() {
            @Override public Path stage(Path target, byte[] bytes, String code) { return stageBytes(target, bytes); }
            @Override public void commit(Path stage, Path target, boolean force, String code) {
                if ("E_REPORT_WRITE".equals(code)) throw new AssetAccessException(code, "simulated report commit failure");
                try { Files.move(stage, target); }
                catch (Exception ex) { throw new AssertionError(ex); }
            }
            @Override public void cleanup(Path stage) { delete(stage); }
        };

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = FileConversion.run(options(input, output, report), stream(stdout), stream(stderr), operations);

        assertEquals(1, exit);
        assertTrue(Files.exists(output));
        assertFalse(Files.exists(report));
        assertTrue(new String(stderr.toByteArray(), StandardCharsets.UTF_8).contains("HTML was saved"));
        assertEquals("", new String(stdout.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test void reportUsesNodeSchemaTwoAndIncludesMetadataAndDiagnosticDetails() throws Exception {
        Path input = temp.resolve("article.md");
        Path output = temp.resolve("article.html");
        Path report = temp.resolve("report.json");
        Files.write(input, ("---\ntitle: Report title\ndescription: Report description\nlang: true\n---\n# Article\n")
                .getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = FileConversion.run(options(input, output, report), stream(stdout), stream(stderr));
        String json = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);

        assertEquals(0, exit);
        assertTrue(json.contains("\"schemaVersion\": \"2\""));
        assertTrue(json.contains("\"imageMode\": \"reference\""));
        assertTrue(json.contains("\"description\": \"Report description\""));
        assertTrue(json.contains("\"details\":{\"field\":\"lang\"}"));
    }

    @Test void missingInputReportOmitsUnknownByteCount() throws Exception {
        Path input = temp.resolve("missing.md");
        Path output = temp.resolve("article.html");
        Path report = temp.resolve("report.json");

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = FileConversion.run(options(input, output, report), stream(stdout), stream(stderr));
        String json = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);

        assertEquals(1, exit);
        assertTrue(json.contains("\"input\": {\"path\": \"" + jsonPath(input) + "\"}"));
        assertFalse(json.contains("\"bytes\": 0"));
        assertTrue(json.contains("\"code\":\"E_INPUT_READ\""));
    }

    @Test void internalStageFailureReturnsThreeAndWritesFailureReport() throws Exception {
        Path input = temp.resolve("article.md");
        Path output = temp.resolve("article.html");
        Path report = temp.resolve("report.json");
        Files.write(input, "# Article\n".getBytes(StandardCharsets.UTF_8));
        FileConversion.OutputFileOperations operations = new FileConversion.OutputFileOperations() {
            @Override public Path stage(Path target, byte[] bytes, String code) { throw new IllegalStateException("injected failure"); }
            @Override public void commit(Path stage, Path target, boolean force, String code) { throw new AssertionError(); }
            @Override public void cleanup(Path stage) { }
        };

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = FileConversion.run(options(input, output, report), stream(stdout), stream(stderr), operations);
        String json = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);

        assertEquals(3, exit);
        assertTrue(json.contains("\"success\": false"));
        assertTrue(json.contains("\"exitCode\": 3"));
        assertTrue(json.contains("\"code\":\"E_INTERNAL\""));
        assertFalse(Files.exists(output));
        assertEquals("", new String(stdout.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test void invalidFrontMatterScanPreventsFailureReportFromOverwritingReferencedImage() throws Exception {
        Path input = temp.resolve("article.md");
        Path output = temp.resolve("article.html");
        Path report = temp.resolve("report.json");
        byte[] originalImage = new byte[] { 11, 22, 33, 44 };
        Files.write(report, originalImage);
        Files.write(input, ("---\ntitle: first\ntitle: duplicate\n---\n![image](report.json)\n")
                .getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CliOptions forced = new CliOptions(input, output, null, null, null, false,
                16L * 1_048_576L, report, true, "reference");
        int exit = FileConversion.run(forced, stream(stdout), stream(stderr));

        assertEquals(1, exit);
        assertArrayEquals(originalImage, Files.readAllBytes(report));
        assertFalse(Files.exists(output));
        assertTrue(new String(stderr.toByteArray(), StandardCharsets.UTF_8).contains("E_OUTPUT_COLLISION"));
    }

    @Test void forceCannotReplaceAnImageUsedAsHtmlOutput() throws Exception {
        Path input = temp.resolve("article.md");
        Path output = temp.resolve("asset.png");
        byte[] originalImage = new byte[] { 21, 22, 23, 24 };
        Files.write(output, originalImage);
        Files.write(input, "![image](asset.png)\n".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CliOptions forced = new CliOptions(input, output, null, null, null, false,
                16L * 1_048_576L, null, true, "reference");
        int exit = FileConversion.run(forced, stream(stdout), stream(stderr));

        assertEquals(1, exit);
        assertArrayEquals(originalImage, Files.readAllBytes(output));
        assertTrue(new String(stderr.toByteArray(), StandardCharsets.UTF_8).contains("E_OUTPUT_COLLISION"));
    }

    @Test void forceCannotReplaceAnImageThroughAReportHardLink() throws Exception {
        Path input = temp.resolve("article.md");
        Path output = temp.resolve("article.html");
        Path image = temp.resolve("asset.png");
        Path report = temp.resolve("report.json");
        byte[] originalImage = new byte[] { 31, 32, 33, 34 };
        Files.write(image, originalImage);
        Files.createLink(report, image);
        Files.write(input, "![image](asset.png)\n".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CliOptions forced = new CliOptions(input, output, null, null, null, false,
                16L * 1_048_576L, report, true, "reference");
        int exit = FileConversion.run(forced, stream(stdout), stream(stderr));

        assertEquals(1, exit);
        assertArrayEquals(originalImage, Files.readAllBytes(image));
        assertArrayEquals(originalImage, Files.readAllBytes(report));
        assertFalse(Files.exists(output));
        assertTrue(new String(stderr.toByteArray(), StandardCharsets.UTF_8).contains("E_OUTPUT_COLLISION"));
    }

    private CliOptions options(Path input, Path output, Path report) {
        return new CliOptions(input, output, null, null, null, false, 16L * 1_048_576L, report, false);
    }

    private static String jsonPath(Path path) { return path.toString().replace("\\", "\\\\").replace("\"", "\\\""); }

    private Path stageBytes(Path target, byte[] bytes) {
        try {
            Path stage = Files.createTempFile(target.getParent(), ".test-stage-", ".tmp");
            Files.write(stage, bytes);
            return stage;
        } catch (Exception ex) { throw new AssertionError(ex); }
    }

    private static void delete(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (Exception ex) { throw new AssertionError(ex); }
    }

    private static byte[] reportBytes(Path report) {
        try { return Files.readAllBytes(report); } catch (Exception ex) { throw new AssertionError(ex); }
    }

    private static PrintStream stream(ByteArrayOutputStream bytes) {
        try { return new PrintStream(bytes, true, "UTF-8"); } catch (Exception ex) { throw new AssertionError(ex); }
    }
}
