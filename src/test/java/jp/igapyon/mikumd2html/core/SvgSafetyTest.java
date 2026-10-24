package jp.igapyon.mikumd2html.core;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SvgSafetyTest {
    private static final String NS = "http://www.w3.org/2000/svg";

    @Test void permitsSafeLocalFragmentCssAndOptionalDimensions() {
        byte[] svg = ("<svg xmlns=\"" + NS + "\"><defs><linearGradient id=\"g\"/></defs>"
                + "<path style=\"fill:url(#g)\" d=\"M0 0\"/><style>path { stroke: url('#g'); }</style></svg>")
                .getBytes(StandardCharsets.UTF_8);
        ImageInspector.Info info = ImageInspector.inspect(svg, "image/svg+xml");
        assertEquals("image/svg+xml", info.getMime());
        assertEquals(0, info.getWidth());
        assertEquals(0, info.getHeight());
    }

    @Test void checksStyleElementEvenWhenItHasNoAttributes() {
        AssetAccessException error = assertThrows(AssetAccessException.class, () -> inspect(
                "<svg xmlns=\"" + NS + "\"><style>@import url(https://example.invalid/a.css);</style></svg>"));
        assertEquals("E_SVG_EXTERNAL_RESOURCE", error.getCode());
    }

    @Test void rejectsEscapedExternalCssUrlsAndMalformedCss() {
        AssetAccessException escaped = assertThrows(AssetAccessException.class, () -> inspect(
                "<svg xmlns=\"" + NS + "\"><path style=\"fill:url(\\68 ttp://example.invalid/a.svg#x)\"/></svg>"));
        assertEquals("E_SVG_EXTERNAL_RESOURCE", escaped.getCode());
        AssetAccessException malformed = assertThrows(AssetAccessException.class, () -> inspect(
                "<svg xmlns=\"" + NS + "\"><style>path { fill: url(#broken }</style></svg>"));
        assertEquals("E_SVG_UNSUPPORTED", malformed.getCode());
    }

    @Test void rejectsProcessingInstructionsWrongNamespacesAndWrongCaseElements() {
        AssetAccessException pi = assertThrows(AssetAccessException.class, () -> inspect(
                "<?xml-stylesheet href=\"https://example.invalid/a.css\"?><svg xmlns=\"" + NS + "\"/>"));
        assertEquals("E_SVG_UNSAFE", pi.getCode());
        AssetAccessException namespace = assertThrows(AssetAccessException.class, () -> inspect(
                "<svg xmlns=\"" + NS + "\" xmlns:x=\"urn:unsafe\"><x:g/></svg>"));
        assertEquals("E_SVG_UNSAFE", namespace.getCode());
        AssetAccessException caseMismatch = assertThrows(AssetAccessException.class, () -> inspect(
                "<svg xmlns=\"" + NS + "\"><lineargradient/></svg>"));
        assertEquals("E_SVG_UNSAFE", caseMismatch.getCode());
    }

    @Test void validatesNestedRasterDataRatherThanOnlyCheckingItsMime() {
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/odcAAAAASUVORK5CYII=";
        ImageInspector.inspect(("<svg xmlns=\"" + NS + "\"><image href=\"data:image/png;base64," + png + "\"/></svg>")
                .getBytes(StandardCharsets.UTF_8), "image/svg+xml");
        AssetAccessException fake = assertThrows(AssetAccessException.class, () -> inspect(
                "<svg xmlns=\"" + NS + "\"><image href=\"data:image/png;base64,YQ==\"/></svg>"));
        assertEquals("E_IMAGE_FORMAT", fake.getCode());
    }

    private static ImageInspector.Info inspect(String source) {
        return ImageInspector.inspect(source.getBytes(StandardCharsets.UTF_8), "image/svg+xml");
    }
}
