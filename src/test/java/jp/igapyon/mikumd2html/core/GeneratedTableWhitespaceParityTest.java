package jp.igapyon.mikumd2html.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import jp.igapyon.mikumd2html.core.AssetAccessException;
import jp.igapyon.mikumd2html.core.AssetInfo;
import jp.igapyon.mikumd2html.core.AssetResolver;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact fixed-output checks for the generated-table whitespace delta in Node v0.6.0. */
class GeneratedTableWhitespaceParityTest {
    private static final Path FIXTURE = Paths.get("src/test/resources/parity/v0.6.0/table-whitespace");

    @Test void repeatedConversionsDoNotExposeRandomInternalMarkers() throws Exception {
        String markdown = new String(Files.readAllBytes(FIXTURE.resolve("inputs/R02.md")), StandardCharsets.UTF_8);
        ConvertOptions options = new ConvertOptions();
        options.setToc(false);
        options.setImageMode("embed");
        options.setAssets(missingImages());

        ConvertResult first = MikuMd2htmlCore.convertMarkdown(markdown, "repeat.md", options);
        ConvertResult second = MikuMd2htmlCore.convertMarkdown(markdown, "repeat.md", options);

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.getHtml(), second.getHtml());
        assertEquals(first.getStats().getHtmlBytes(), second.getStats().getHtmlBytes());
        assertEquals(flatten(first), flatten(second), "source positions and diagnostics must not depend on marker UUIDs");
        assertFalse(first.getHtml().matches("(?s).*data-miku-generated-table-[0-9a-f]{32}.*"));
    }

    @Test void generatedTableCasesMatchPinnedNodeV060() throws Exception {
        Properties cases = load(FIXTURE.resolve("cases.properties"));
        List<String> ids = Arrays.asList(cases.getProperty("case.ids", "").split(","));
        assertEquals(33, ids.size(), "the tracked Node v0.6.0 table-whitespace case count changed");
        int exactCount = 0;
        int regressionCount = 0;
        int knownDifferenceCount = 0;
        Set<String> scopes = new LinkedHashSet<String>();
        for (String id : ids) {
            String scope = required(cases, "case." + id + ".scope");
            scopes.add(scope);
            verifyOracleHashes(id, cases);
            if ("known-difference".equals(scope)) {
                assertKnownDifference(id, cases);
                knownDifferenceCount++;
                continue;
            }
            assertTrue("exact".equals(scope) || "regression".equals(scope), id + " has unsupported comparison scope " + scope);
            compareOutput(id, cases);
            if ("exact".equals(scope)) exactCount++;
            else regressionCount++;
        }
        assertEquals(24, exactCount, "exact parity case count changed");
        assertEquals(7, regressionCount, "scoped regression case count changed");
        assertEquals(2, knownDifferenceCount, "known-difference case count changed");
        assertEquals(new LinkedHashSet<String>(Arrays.asList("exact", "regression", "known-difference")), scopes);
    }

    private static void compareOutput(String id, Properties cases) throws Exception {
        String prefix = "case." + id + ".";
        String inputPath = required(cases, prefix + "input");
        String inputName = required(cases, prefix + "inputName");
        String markdown = new String(Files.readAllBytes(FIXTURE.resolve(inputPath)), StandardCharsets.UTF_8);
        ConvertOptions options = new ConvertOptions();
        options.setToc(Boolean.parseBoolean(required(cases, prefix + "toc")));
        options.setImageMode(required(cases, prefix + "imageMode"));
        if (id.startsWith("R01-") || "R02".equals(id)) options.setAssets(missingImages());
        ConvertResult actual = MikuMd2htmlCore.convertMarkdown(markdown, inputName, options);

        Properties expected = load(FIXTURE.resolve("expected").resolve(id + ".contract.properties"));
        Map<String, String> flattened = flatten(actual);
        if (!actual.isSuccess()) flattened.remove("stats.htmlBytes");
        Set<String> ignored = ignoredFields(cases.getProperty(prefix + "ignore", ""));
        for (String key : expected.stringPropertyNames()) {
            if (key.startsWith("oracle.") || ignored.contains(key)) continue;
            assertEquals(expected.getProperty(key), flattened.get(key), id + " " + key);
        }
        for (String key : flattened.keySet()) {
            if (ignored.contains(key)) continue;
            assertTrue(expected.containsKey(key), id + " unexpected Java contract field " + key);
        }
        if (id.startsWith("R05-")) assertSingleLfBoundaryDifference(id);
        assertEquals(expected.getProperty("html.present"), String.valueOf(actual.getHtml() != null), id + " HTML presence");
        if (actual.getHtml() != null) {
            byte[] expectedHtml = Files.readAllBytes(FIXTURE.resolve("expected").resolve(id + ".node.html"));
            byte[] actualHtml = actual.getHtml().getBytes(StandardCharsets.UTF_8);
            if ("T14".equals(id)) {
                String expectedHtmlText = new String(expectedHtml, StandardCharsets.UTF_8);
                String adjusted = expectedHtmlText.replace("</thead></table>", "</thead><tbody></tbody></table>");
                assertNotEquals(expectedHtmlText, adjusted, "the T14 regression exception must remain specific");
                assertArrayEquals(adjusted.getBytes(StandardCharsets.UTF_8), actualHtml,
                        "T14 differs only because jsoup retains its empty tbody wrapper");
                assertTrue(required(cases, prefix + "reason").contains("empty tbody"));
            } else if (id.startsWith("R05-")) {
                assertEquals(tableBoundaryLineFeeds(new String(expectedHtml, StandardCharsets.UTF_8)),
                        tableBoundaryLineFeeds(actual.getHtml()), "TOC placement must preserve the table's immediate whitespace");
            } else {
                assertArrayEquals(expectedHtml, actualHtml, id + " HTML bytes");
            }
            assertFalse(actual.getHtml().matches("(?s).*data-miku-generated-table-[0-9a-f]{32}.*"), id + " leaked a generated table marker");
            assertFalse(actual.getHtml().contains("data-miku-image-source-"), id + " leaked an image source marker");
        }
    }

    private static void assertKnownDifference(String id, Properties cases) throws Exception {
        String prefix = "case." + id + ".";
        String reason = required(cases, prefix + "reason");
        String markdown = new String(Files.readAllBytes(FIXTURE.resolve(required(cases, prefix + "input"))), StandardCharsets.UTF_8);
        ConvertOptions options = new ConvertOptions();
        options.setToc(Boolean.parseBoolean(required(cases, prefix + "toc")));
        options.setImageMode(required(cases, prefix + "imageMode"));
        if (id.startsWith("R01-") || "R02".equals(id)) options.setAssets(missingImages());
        ConvertResult actual = MikuMd2htmlCore.convertMarkdown(markdown, required(cases, prefix + "inputName"), options);
        String expected = new String(Files.readAllBytes(FIXTURE.resolve("expected").resolve(id + ".node.html")), StandardCharsets.UTF_8);
        assertNotEquals(expected, actual.getHtml(), id + " is recorded as a known difference: " + reason);
        if ("K01".equals(id)) {
            assertEquals(1, Jsoup.parse(expected).select("article table").size(), "Node recognizes the compact list table");
            assertEquals(0, Jsoup.parse(actual.getHtml()).select("article table").size(), "Java keeps the documented paragraph parse");
        }
    }

    private static void verifyOracleHashes(String id, Properties cases) throws Exception {
        Properties expected = load(FIXTURE.resolve("expected").resolve(id + ".contract.properties"));
        byte[] input = Files.readAllBytes(FIXTURE.resolve(required(cases, "case." + id + ".input")));
        byte[] html = Files.readAllBytes(FIXTURE.resolve("expected").resolve(id + ".node.html"));
        byte[] json = Files.readAllBytes(FIXTURE.resolve("expected").resolve(id + ".node.json"));
        assertEquals(sha256(input), expected.getProperty("oracle.input.sha256"), id + " input oracle hash");
        assertEquals(sha256(html), expected.getProperty("oracle.html.sha256"), id + " HTML oracle hash");
        assertEquals(sha256(json), expected.getProperty("oracle.json.sha256"), id + " result oracle hash");
        if (Boolean.parseBoolean(expected.getProperty("html.present")))
            assertEquals(expected.getProperty("html.sha256"), expected.getProperty("oracle.html.sha256"), id + " contract HTML hash");
        else assertFalse(expected.containsKey("html.sha256"), id + " failed conversion must not have an HTML hash");
    }

    private static Set<String> ignoredFields(String value) {
        Set<String> fields = new LinkedHashSet<String>();
        for (String item : value.split(",")) if (!item.trim().isEmpty()) fields.add(item.trim());
        return fields;
    }

    private static void assertSingleLfBoundaryDifference(String id) throws Exception {
        String expected = new String(Files.readAllBytes(FIXTURE.resolve("expected").resolve(id + ".node.html")), StandardCharsets.UTF_8);
        Properties cases = load(FIXTURE.resolve("cases.properties"));
        String markdown = new String(Files.readAllBytes(FIXTURE.resolve(required(cases, "case." + id + ".input"))), StandardCharsets.UTF_8);
        ConvertOptions options = new ConvertOptions();
        options.setToc(true);
        options.setImageMode("embed");
        String actual = MikuMd2htmlCore.convertMarkdown(markdown, id + ".md", options).getHtml();
        assertNotEquals(expected, actual, "the recorded TOC boundary regression must remain observable");
        int prefix = 0;
        while (prefix < expected.length() && prefix < actual.length() && expected.charAt(prefix) == actual.charAt(prefix)) prefix++;
        int expectedEnd = expected.length();
        int actualEnd = actual.length();
        while (expectedEnd > prefix && actualEnd > prefix && expected.charAt(expectedEnd - 1) == actual.charAt(actualEnd - 1)) {
            expectedEnd--;
            actualEnd--;
        }
        assertTrue((expectedEnd - prefix == 1 && actualEnd == prefix)
                || (actualEnd - prefix == 1 && expectedEnd == prefix), "the TOC boundary delta must be one byte");
        assertEquals("\n", expectedEnd - prefix == 1 ? expected.substring(prefix, expectedEnd) : actual.substring(prefix, actualEnd));
    }

    private static int tableBoundaryLineFeeds(String html) {
        int article = html.indexOf("<article>");
        int table = html.indexOf("<table", article < 0 ? 0 : article);
        if (table < 0) return -1;
        int count = 0;
        for (int index = table - 1; index >= 0 && html.charAt(index) == '\n'; index--) count++;
        return count;
    }

    private static Properties load(Path path) throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(path)) { properties.load(stream); }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        assertNotNull(value, "missing fixture property " + key);
        return value;
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

    private static AssetResolver missingImages() {
        return new AssetResolver() {
            @Override public AssetInfo describe(String path) {
                throw new AssetAccessException("E_IMAGE_NOT_FOUND", "missing fixture image");
            }
            @Override public byte[] read(String id, long maxBytes) {
                throw new AssetAccessException("E_IMAGE_NOT_FOUND", "missing fixture image");
            }
            @Override public String reference(String path) { return "./" + path; }
        };
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder output = new StringBuilder();
        for (byte item : digest) output.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return output.toString();
    }
}
