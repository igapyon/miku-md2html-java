package jp.igapyon.mikumd2html.core;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageEmbeddingSessionTest {
    private static final String PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/odcAAAAASUVORK5CYII=";
    private static final byte[] PNG = Base64.getDecoder().decode(PNG_BASE64);

    @Test void cachesDescriptionsByPathAndReadsTheSameAssetIdOnce() {
        CountingResolver resolver = new CountingResolver(PNG);
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        ImageEmbeddingSession session = new ImageEmbeddingSession(resolver, diagnostics, 1_000_000L);

        String first = session.renderImage("pixel", "pixel.png", null, 1, 1);
        String repeatedPath = session.renderImage("same", "pixel.png", null, 2, 1);
        String alias = session.renderImage("alias", "alias.png", null, 3, 1);

        assertTrue(first.contains("<img src=\"data:image/png;base64," + PNG_BASE64));
        assertTrue(repeatedPath.contains("<img src=\"data:image/png;base64,"));
        assertTrue(alias.contains("<img src=\"data:image/png;base64,"));
        assertEquals(2, resolver.describeCalls);
        assertEquals(1, resolver.readCalls);
        assertEquals(3, session.getOccurrences());
        assertEquals(1, session.getUniqueAssets());
        assertEquals(PNG.length, session.getUniqueSourceAssetBytes());
        assertEquals(3L * (((PNG.length + 2L) / 3L) * 4L), session.getEmbeddedPayloadBytes());
        assertTrue(diagnostics.isEmpty());
    }

    @Test void reportsInconsistentSizeForTheSameResolverId() {
        CountingResolver resolver = new CountingResolver(PNG);
        resolver.reportedLengths.put("alias.png", (long) PNG.length + 1L);
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        ImageEmbeddingSession session = new ImageEmbeddingSession(resolver, diagnostics, 1_000_000L);

        session.renderImage("pixel", "pixel.png", null, 1, 1);
        String fallback = session.renderImage("alias", "alias.png", null, 2, 1);

        assertEquals("alias", fallback);
        assertEquals(1, resolver.readCalls);
        assertEquals("E_ASSET_CHANGED", diagnostics.get(0).getCode());
    }

    @Test void mapsInvalidResolverMetadataToImageReadErrorAndPropagatesUnexpectedFailures() {
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        AssetResolver invalid = new AssetResolver() {
            @Override public AssetInfo describe(String path) { return null; }
            @Override public byte[] read(String id, long maxBytes) { throw new AssertionError("read must not be called"); }
        };
        ImageEmbeddingSession session = new ImageEmbeddingSession(invalid, diagnostics, 1_000_000L);
        session.renderImage("pixel", "pixel.png", null, 1, 1);
        assertEquals("E_IMAGE_READ", diagnostics.get(0).getCode());

        AssetResolver broken = new AssetResolver() {
            @Override public AssetInfo describe(String path) { throw new IllegalStateException("resolver contract failure"); }
            @Override public byte[] read(String id, long maxBytes) { throw new AssertionError("read must not be called"); }
        };
        ImageEmbeddingSession second = new ImageEmbeddingSession(broken, new ArrayList<Diagnostic>(), 1_000_000L);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> second.renderImage("pixel", "pixel.png", null, 1, 1));
        assertEquals("resolver contract failure", failure.getMessage());
    }

    private static final class CountingResolver implements AssetResolver {
        final Map<String, Long> reportedLengths = new HashMap<String, Long>();
        final byte[] bytes;
        int describeCalls;
        int readCalls;

        CountingResolver(byte[] bytes) { this.bytes = bytes; }

        @Override public AssetInfo describe(String path) {
            describeCalls++;
            Long length = reportedLengths.get(path);
            return new AssetInfo("canonical-id", path, length == null ? bytes.length : length.longValue());
        }

        @Override public byte[] read(String id, long maxBytes) {
            readCalls++;
            return bytes.clone();
        }
    }
}
