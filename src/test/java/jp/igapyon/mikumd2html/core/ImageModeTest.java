package jp.igapyon.mikumd2html.core;

import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageModeTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/odcAAAAASUVORK5CYII=");

    @Test void defaultsToReferenceAndKeepsDataUrlsEmbedded() {
        Resolver resolver = new Resolver();
        ConvertOptions options = new ConvertOptions();
        options.setAssets(resolver);
        ConvertResult result = MikuMd2htmlCore.convertMarkdown("![pixel](pixel.png)", "image.md", options);
        assertTrue(result.isSuccess(), () -> result.getDiagnostics().toString());
        assertTrue(result.getHtml().contains("src=\"./pixel.png\""));
        assertTrue(result.getHtml().contains("img-src data: file: &#39;self&#39;"));
        assertEquals(1, result.getStats().getReferencedImageOccurrences());
        assertEquals(0, result.getStats().getEmbeddedImageOccurrences());
        assertEquals(0, result.getStats().getEmbeddedPayloadBytes());

        String data = "![pixel](data:image/png;base64," + Base64.getEncoder().encodeToString(PNG) + ")";
        ConvertResult dataResult = MikuMd2htmlCore.convertMarkdown(data, "image.md", null);
        assertTrue(dataResult.isSuccess(), () -> dataResult.getDiagnostics().toString());
        assertTrue(dataResult.getHtml().contains("src=\"data:image/png;base64,"));
        assertEquals(1, dataResult.getStats().getEmbeddedImageOccurrences());
    }

    @Test void explicitEmbedWorksAndOldResolverReferenceFailsWithRequiredCode() {
        Resolver resolver = new Resolver();
        ConvertOptions options = new ConvertOptions();
        options.setAssets(resolver);
        options.setImageMode("embed");
        ConvertResult embedded = MikuMd2htmlCore.convertMarkdown("![pixel](pixel.png)", "image.md", options);
        assertTrue(embedded.isSuccess(), () -> embedded.getDiagnostics().toString());
        assertTrue(embedded.getHtml().contains("src=\"data:image/png;base64,"));
        assertTrue(embedded.getHtml().contains("img-src data:;"));
        assertEquals(1, embedded.getStats().getEmbeddedImageOccurrences());

        AssetResolver oldResolver = new AssetResolver() {
            @Override public AssetInfo describe(String path) { return new AssetInfo("pixel-id", "pixel.png", PNG.length); }
            @Override public byte[] read(String id, long maxBytes) { return PNG.clone(); }
        };
        ConvertResult reference = MikuMd2htmlCore.convertMarkdown("![pixel](pixel.png)", "image.md", newOptions(oldResolver, "reference"));
        assertFalse(reference.isSuccess());
        assertTrue(reference.getDiagnostics().stream().anyMatch(d -> "E_ASSET_REFERENCE_REQUIRED".equals(d.getCode())));
    }

    @Test void invalidImageModesAreOptionsErrors() {
        for (String mode : new String[] { null, "", "Reference", " embed ", "copy" }) {
            ConvertOptions options = new ConvertOptions();
            options.setImageMode(mode);
            ConvertResult result = MikuMd2htmlCore.convertMarkdown("", "empty.md", options);
            assertFalse(result.isSuccess(), String.valueOf(mode));
            assertEquals("E_OPTIONS", result.getDiagnostics().get(0).getCode(), String.valueOf(mode));
        }
    }

    private static ConvertOptions newOptions(AssetResolver resolver, String mode) {
        ConvertOptions options = new ConvertOptions(); options.setAssets(resolver); options.setImageMode(mode); return options;
    }

    private static final class Resolver implements AssetResolver {
        final List<String> described = new ArrayList<String>();
        @Override public AssetInfo describe(String path) { described.add(path); return new AssetInfo("pixel-id", "pixel.png", PNG.length); }
        @Override public byte[] read(String id, long maxBytes) { return PNG.clone(); }
        @Override public String reference(String path) { return "./" + path; }
    }
}
