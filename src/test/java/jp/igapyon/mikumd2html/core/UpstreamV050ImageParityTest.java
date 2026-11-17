package jp.igapyon.mikumd2html.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.LinkedHashSet;
import java.util.Set;
import jp.igapyon.mikumd2html.core.AssetAccessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks image behavior against the Node v0.5.0 oracle while allowing the intentional v0.6 renderer delta. */
class UpstreamV050ImageParityTest {
    private static final Path FIXTURE = Paths.get("src/test/resources/parity/v0.5.0");
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAO+/odcAAAAASUVORK5CYII=");

    @Test void imageOutputsDiagnosticsAndAccountingMatchPinnedNodeV050() throws Exception {
        List<Path> inputs = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(FIXTURE.resolve("inputs"), "*.md")) {
            for (Path input : stream) inputs.add(input);
        }
        Collections.sort(inputs);
        assertEquals(27, inputs.size(), "the pinned v0.5.0 image oracle case count changed");

        Set<String> sourceDifferences = new LinkedHashSet<String>();
        for (Path input : inputs) compareCase(input, sourceDifferences);
        assertTrue(sourceDifferences.isEmpty(), "Node v0.5.0 image source ranges differ: " + sourceDifferences);
    }

    @Test void invalidFrontMatterCompletesReferenceScanWithoutCallingAssetIo() {
        final List<String> references = new ArrayList<String>();
        final int[] callbacks = new int[1];
        AssetResolver resolver = new AssetResolver() {
            @Override public AssetInfo describe(String path) { callbacks[0]++; throw new AssertionError("describe must not run"); }
            @Override public byte[] read(String id, long maxBytes) { callbacks[0]++; throw new AssertionError("read must not run"); }
        };
        ImageReferenceTracker tracker = new ImageReferenceTracker() {
            @Override public void recordReference(String path) { references.add(path); }
            @Override public void completeReferenceScan() { callbacks[0] += 100; }
            @Override public boolean isReferenceScanComplete() { return callbacks[0] >= 100; }
        };
        AssetResolver tracked = new TrackingAssetResolver(resolver, tracker);
        ConvertOptions options = new ConvertOptions();
        options.setAssets(tracked);

        ConvertResult result = MikuMd2htmlCore.convertMarkdown(
                "---\ntitle: one\ntitle: two\n---\n![diagram](images/diagram.png)\n", "bad.md", options);

        assertFalse(result.isSuccess());
        assertEquals(Collections.singletonList("images/diagram.png"), references);
        assertEquals(100, callbacks[0], "only completeReferenceScan should be called");
    }

    private static void compareCase(Path input, Set<String> sourceDifferences) throws Exception {
        String id = input.getFileName().toString().replaceFirst("\\.md$", "");
        String mode = id.startsWith("embed-") ? "embed" : "reference";
        String scenario = id.substring(id.indexOf('-') + 1);
        String markdown = new String(Files.readAllBytes(input), StandardCharsets.UTF_8);
        ConvertOptions options = new ConvertOptions();
        options.setImageMode(mode);
        AssetResolver resolver = resolver(scenario);
        if (resolver != null) options.setAssets(resolver);
        ConvertResult actual = MikuMd2htmlCore.convertMarkdown(markdown, id + ".md", options);

        Properties expected = new Properties();
        try (InputStream stream = Files.newInputStream(FIXTURE.resolve("expected").resolve(id + ".contract.properties"))) {
            expected.load(stream);
        }
        Map<String, String> flattened = flatten(actual);
        for (String key : expected.stringPropertyNames()) {
            if (isSourcePositionField(key)) {
                if (!expected.getProperty(key).equals(flattened.get(key)))
                    sourceDifferences.add(id + " " + key + " Node=" + expected.getProperty(key) + " Java=" + flattened.get(key));
                continue;
            }
            if (isUncomparedField(key)) continue;
            assertEquals(expected.getProperty(key), flattened.get(key), id + " " + key);
        }
        for (String key : flattened.keySet()) {
            if (isSourcePositionField(key) || isUncomparedField(key)) continue;
            assertTrue(expected.containsKey(key), id + " unexpected Node contract field " + key);
        }
        assertImageElementsEqual(id, Boolean.parseBoolean(expected.getProperty("html.present")),
                expectedHtml(id), actual.getHtml());
        if (actual.getHtml() != null) assertFalse(actual.getHtml().contains("data-miku-image-source-"), id + " leaked an internal source marker");
    }

    private static boolean isUncomparedField(String key) {
        return "html.sha256".equals(key) || "stats.htmlBytes".equals(key);
    }

    private static boolean isSourcePositionField(String key) {
        return key.matches("diagnostic\\.\\d+\\.source\\.(?:start|end)\\..+");
    }

    private static String expectedHtml(String id) throws Exception {
        Path path = FIXTURE.resolve("expected").resolve(id + ".node.html");
        return Files.exists(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : null;
    }

    private static void assertImageElementsEqual(String id, boolean expectedPresent, String expectedHtml, String actualHtml) {
        if (!expectedPresent || actualHtml == null) {
            assertEquals(expectedPresent, actualHtml != null, id + " HTML presence");
            return;
        }
        List<Element> expected = Jsoup.parse(expectedHtml).select("article img");
        List<Element> actual = Jsoup.parse(actualHtml).select("article img");
        assertEquals(expected.size(), actual.size(), id + " image count");
        String[] attributes = { "src", "alt", "title", "width", "height" };
        for (int i = 0; i < expected.size(); i++) {
            for (String attribute : attributes) {
                assertEquals(expected.get(i).attr(attribute), actual.get(i).attr(attribute),
                        id + " image[" + i + "]." + attribute);
            }
        }
    }

    private static Map<String, String> flatten(ConvertResult result) throws Exception {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("success", String.valueOf(result.isSuccess()));
        values.put("html.present", String.valueOf(result.getHtml() != null));
        if (result.getHtml() != null) values.put("html.sha256", sha256(result.getHtml().getBytes(StandardCharsets.UTF_8)));
        put(values, "metadata.title", result.getTitle());
        put(values, "metadata.lang", result.getLang());
        put(values, "metadata.description", result.getDescription());
        ConversionStats stats = result.getStats();
        values.put("stats.headingCount", String.valueOf(stats.getHeadingCount()));
        values.put("stats.imageOccurrences", String.valueOf(stats.getImageOccurrences()));
        values.put("stats.uniqueAssets", String.valueOf(stats.getUniqueAssets()));
        values.put("stats.uniqueSourceAssetBytes", String.valueOf(stats.getUniqueSourceAssetBytes()));
        values.put("stats.embeddedPayloadBytes", String.valueOf(stats.getEmbeddedPayloadBytes()));
        values.put("stats.embeddedImageOccurrences", String.valueOf(stats.getEmbeddedImageOccurrences()));
        values.put("stats.referencedImageOccurrences", String.valueOf(stats.getReferencedImageOccurrences()));
        values.put("stats.omittedImageOccurrences", String.valueOf(stats.getOmittedImageOccurrences()));
        values.put("stats.htmlBytes", String.valueOf(stats.getHtmlBytes()));
        values.put("diagnostics.count", String.valueOf(result.getDiagnostics().size()));
        for (int i = 0; i < result.getDiagnostics().size(); i++) {
            Diagnostic diagnostic = result.getDiagnostics().get(i);
            String prefix = "diagnostic." + i + ".";
            values.put(prefix + "severity", diagnostic.getSeverity());
            values.put(prefix + "code", diagnostic.getCode());
            values.put(prefix + "message", diagnostic.getMessage());
            put(values, prefix + "source.label", diagnostic.getLabel());
            putPosition(values, prefix + "source.start", diagnostic.getStart());
            putPosition(values, prefix + "source.end", diagnostic.getEnd());
            for (Map.Entry<String, Object> detail : diagnostic.getDetails().entrySet())
                values.put(prefix + "details." + detail.getKey(), String.valueOf(detail.getValue()));
        }
        return values;
    }

    private static void putPosition(Map<String, String> values, String prefix, SourcePosition position) {
        if (position == null) return;
        values.put(prefix + ".line", String.valueOf(position.getLine()));
        values.put(prefix + ".column", String.valueOf(position.getColumn()));
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null) values.put(key, value);
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder output = new StringBuilder();
        for (byte item : digest) output.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return output.toString();
    }

    private static AssetResolver resolver(final String scenario) throws Exception {
        if ("remote".equals(scenario) || "data-png".equals(scenario)) return null;
        final byte[] bytes;
        if (scenario.equals("png") || scenario.equals("raw-img") || scenario.equals("svg-named-png")
                || scenario.equals("repeated-alias") || scenario.equals("no-reference")
                || scenario.equals("old-resolver")) bytes = PNG;
        else if (scenario.equals("svg") || scenario.equals("svg-old-resolver")) bytes = Files.readAllBytes(FIXTURE.resolve("valid.svg"));
        else if (scenario.equals("unsafe-svg")) bytes = Files.readAllBytes(FIXTURE.resolve("unsafe.svg"));
        else if (scenario.equals("invalid-png")) bytes = "not a png".getBytes(StandardCharsets.UTF_8);
        else if (scenario.startsWith("missing-")) bytes = null;
        else if (scenario.equals("outside")) bytes = null;
        else throw new AssertionError("Unknown oracle scenario: " + scenario);

        return new PrefixAssetResolver() {
            @Override public AssetInfo describe(String path) {
                if ("outside".equals(scenario)) throw new AssetAccessException("E_ASSET_OUTSIDE_ROOT", "outside");
                if (scenario.startsWith("missing-")) throw new AssetAccessException("E_IMAGE_NOT_FOUND", "missing");
                String id = "repeated-alias".equals(scenario) ? "shared-id" : scenario + "-id";
                return new AssetInfo(id, path, bytes.length);
            }

            @Override public byte[] read(String id, long maxBytes) {
                if ("outside".equals(scenario)) throw new AssetAccessException("E_ASSET_OUTSIDE_ROOT", "outside");
                if (bytes.length > maxBytes) throw new AssetAccessException("E_OUTPUT_TOO_LARGE", "too large");
                return bytes.clone();
            }

            @Override public byte[] readPrefix(String id, long maxBytes) {
                int length = (int) Math.min(maxBytes, bytes.length);
                byte[] prefix = new byte[length];
                System.arraycopy(bytes, 0, prefix, 0, length);
                return prefix;
            }

            @Override public String reference(String path) {
                if ("outside".equals(scenario)) throw new AssetAccessException("E_ASSET_OUTSIDE_ROOT", "outside");
                return "./" + path;
            }
        };
    }

    private static final class TrackingAssetResolver implements AssetResolver, ImageReferenceTracker {
        private final AssetResolver delegate;
        private final ImageReferenceTracker tracker;
        TrackingAssetResolver(AssetResolver delegate, ImageReferenceTracker tracker) {
            this.delegate = delegate;
            this.tracker = tracker;
        }
        @Override public AssetInfo describe(String path) { return delegate.describe(path); }
        @Override public byte[] read(String id, long maxBytes) { return delegate.read(id, maxBytes); }
        @Override public String reference(String path) { return delegate.reference(path); }
        @Override public void recordReference(String path) { tracker.recordReference(path); }
        @Override public void completeReferenceScan() { tracker.completeReferenceScan(); }
        @Override public boolean isReferenceScanComplete() { return tracker.isReferenceScanComplete(); }
    }
}
