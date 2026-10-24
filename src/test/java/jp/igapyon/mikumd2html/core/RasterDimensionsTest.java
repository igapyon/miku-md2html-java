package jp.igapyon.mikumd2html.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class RasterDimensionsTest {
    @Test void readsCgbiPngDimensionsAndReportsLargeDimensionsOnce() {
        byte[] png = cgbiPng(10000, 5000);
        ImageInspector.Info info = ImageInspector.inspect(png, "image/png");
        assertEquals(10000L, info.getWidth());
        assertEquals(5000L, info.getHeight());

        String encoded = Base64.getEncoder().encodeToString(png);
        ConvertResult result = MikuMd2htmlCore.convertMarkdown(
                "![large](data:image/png;base64," + encoded + ")\n\n![again](data:image/png;base64," + encoded + ")", "large.md", null);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getDiagnostics().stream().filter(d -> "W_LARGE_IMAGE_DIMENSIONS".equals(d.getCode())).count());
        Diagnostic warning = result.getDiagnostics().stream().filter(d -> "W_LARGE_IMAGE_DIMENSIONS".equals(d.getCode())).findFirst().get();
        assertEquals(10000L, warning.getDetails().get("width"));
        assertEquals(5000L, warning.getDetails().get("height"));
    }

    @Test void readsJpegHeightFromDnlAfterEntropyData() {
        byte[] jpeg = bytes(0xff, 0xd8,
                0xff, 0xc0, 0, 11, 8, 0, 0, 0, 32, 1, 1, 0x11, 0,
                0xff, 0xda, 0, 2,
                0x12, 0x34, 0xff, 0x00, 0x56,
                0xff, 0xdc, 0, 4, 0, 24,
                0xff, 0xd9);
        ImageInspector.Info info = ImageInspector.inspect(jpeg, "image/jpeg");
        assertEquals(32L, info.getWidth());
        assertEquals(24L, info.getHeight());
    }

    @Test void skipsUnknownWebpChunksAndRejectsZeroOrMalformedDimensions() {
        byte[] webp = webp(chunk("JUNK", bytes(1, 2, 3)), chunk("VP8X", bytes(0, 0, 0, 0, 9, 0, 0, 9, 0, 0)));
        ImageInspector.Info info = ImageInspector.inspect(webp, "image/webp");
        assertEquals(10L, info.getWidth());
        assertEquals(10L, info.getHeight());

        byte[] zeroPng = pngWithDimensions(0, 1);
        assertInvalid(zeroPng, "image/png");
        assertInvalid(webp(chunk("JUNK", bytes(1, 2, 3, 4))), "image/webp");
    }

    private static void assertInvalid(byte[] data, String mime) {
        AssetAccessException error = assertThrows(AssetAccessException.class, () -> ImageInspector.inspect(data, mime));
        assertEquals("E_IMAGE_INVALID", error.getCode());
    }

    private static byte[] pngWithDimensions(long width, long height) {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z2q0AAAAASUVORK5CYII=");
        put32be(png, 16, width); put32be(png, 20, height);
        return png;
    }

    private static byte[] cgbiPng(long width, long height) {
        return concat(bytes(137, 80, 78, 71, 13, 10, 26, 10),
                pngChunk("CgBI", bytes(0, 0, 0, 0)),
                pngChunk("IHDR", concat(uint32be(width), uint32be(height), bytes(8, 6, 0, 0, 0))),
                pngChunk("IDAT", new byte[0]), pngChunk("IEND", new byte[0]));
    }

    private static byte[] webp(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, bytes('W', 'E', 'B', 'P'));
        for (byte[] chunk : chunks) write(body, chunk);
        byte[] contents = body.toByteArray();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        write(file, bytes('R', 'I', 'F', 'F'));
        write(file, uint32le(contents.length));
        write(file, contents);
        return file.toByteArray();
    }

    private static byte[] chunk(String name, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, name.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        write(out, uint32le(payload.length));
        write(out, payload);
        if ((payload.length & 1) != 0) out.write(0);
        return out.toByteArray();
    }

    private static byte[] pngChunk(String name, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, uint32be(payload.length));
        write(out, name.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        write(out, payload);
        write(out, bytes(0, 0, 0, 0));
        return out.toByteArray();
    }

    private static byte[] uint32be(long n) { return bytes((int) (n >>> 24), (int) (n >>> 16), (int) (n >>> 8), (int) n); }
    private static byte[] uint32le(long n) { return bytes((int) n, (int) (n >>> 8), (int) (n >>> 16), (int) (n >>> 24)); }
    private static void put32be(byte[] b, int offset, long n) { for (int i = 0; i < 4; i++) b[offset + i] = (byte) (n >>> (24 - 8 * i)); }
    private static byte[] bytes(int... values) { byte[] result = new byte[values.length]; for (int i = 0; i < values.length; i++) result[i] = (byte) values[i]; return result; }
    private static byte[] concat(byte[]... values) { ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] value : values) write(out, value); return out.toByteArray(); }
    private static void write(ByteArrayOutputStream out, byte[] value) { out.write(value, 0, value.length); }
}
