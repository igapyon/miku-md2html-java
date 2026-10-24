package jp.igapyon.mikumd2html.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImageUriTest {
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z2q0AAAAASUVORK5CYII=");

    @Test void decodesEncodedPathCharactersExactlyOnce() {
        for (String[] pair : new String[][] {
                {"images/a%23b.png", "images/a#b.png"},
                {"images/a%3Fb.png", "images/a?b.png"},
                {"images/a%2523b.png", "images/a%23b.png"}
        }) {
            List<String> paths = new ArrayList<String>();
            ConvertResult result = convert(pair[0], paths);
            assertTrue(result.isSuccess(), () -> result.getDiagnostics().toString());
            assertEquals(pair[1], paths.get(0));
        }
    }

    @Test void rejectsRawDelimitersAndInvalidEncodedPathBytes() {
        for (String destination : new String[] {
                "images/a#b.png", "images/a?b.png", "images/%00.png", "images/a%5Cb.png",
                "%2Fetc/passwd", "C%3A/image.png", "images/%GG.png", "images/%FF.png"
        }) {
            ConvertResult result = convert(destination, new ArrayList<String>());
            assertFalse(result.isSuccess(), destination);
            assertEquals("E_IMAGE_URI", result.getDiagnostics().get(0).getCode(), destination);
        }
    }

    private static ConvertResult convert(String destination, List<String> paths) {
        ConvertOptions options = new ConvertOptions();
        options.setImageMode("embed");
        options.setAssets(new AssetResolver() {
            @Override public AssetInfo describe(String relativePath) {
                paths.add(relativePath);
                return new AssetInfo(relativePath, relativePath, PNG.length);
            }
            @Override public byte[] read(String id, long maxBytes) { return PNG; }
        });
        return MikuMd2htmlCore.convertMarkdown("![image](" + destination + ")", "image.md", options);
    }
}
