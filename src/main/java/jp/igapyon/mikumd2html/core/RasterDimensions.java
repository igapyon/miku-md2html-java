package jp.igapyon.mikumd2html.core;

/** Reads raster dimensions using the same bounded header walk as the Node runtime. */
final class RasterDimensions {
    static final class Size {
        final long width;
        final long height;
        Size(long width, long height) { this.width = width; this.height = height; }
    }

    private RasterDimensions() { }

    static Size read(byte[] bytes, String mime) {
        if ("image/png".equals(mime)) return png(bytes);
        if ("image/jpeg".equals(mime)) return jpeg(bytes);
        if ("image/gif".equals(mime)) return gif(bytes);
        if ("image/webp".equals(mime)) return webp(bytes);
        return null;
    }

    private static Size png(byte[] b) {
        int ihdr = 8;
        if (ascii(b, 12, "CgBI")) {
            if (u32be(b, 8) != 4) return null;
            ihdr = 24;
        }
        if (b.length < ihdr + 16 || u32be(b, ihdr) != 13 || !ascii(b, ihdr + 4, "IHDR")) return null;
        return dimensions(u32be(b, ihdr + 8), u32be(b, ihdr + 12));
    }

    private static Size gif(byte[] b) {
        if (b.length < 10 || !(ascii(b, 0, "GIF87a") || ascii(b, 0, "GIF89a"))) return null;
        return dimensions(u16le(b, 6), u16le(b, 8));
    }

    private static Size jpeg(byte[] b) {
        int offset = 2;
        boolean entropy = false;
        long zeroHeightWidth = -1;
        while (offset < b.length) {
            if (entropy) {
                boolean markerFound = false;
                while (offset < b.length) {
                    if (u8(b, offset) != 0xff) { offset++; continue; }
                    int markerOffset = offset++;
                    while (offset < b.length && u8(b, offset) == 0xff) offset++;
                    if (offset >= b.length) return null;
                    int marker = u8(b, offset);
                    if (marker == 0x00 || isRestart(marker)) { offset++; continue; }
                    offset = markerOffset;
                    entropy = false;
                    markerFound = true;
                    break;
                }
                if (!markerFound) return null;
            }
            if (u8(b, offset) != 0xff) return null;
            while (offset < b.length && u8(b, offset) == 0xff) offset++;
            if (offset >= b.length) return null;
            int marker = u8(b, offset++);
            if (marker == 0x00) return null;
            if (marker == 0xd9) return zeroHeightWidth < 0 ? null : dimensions(zeroHeightWidth, 0);
            if (marker == 0xd8) return null;
            if (marker == 0x01 || isRestart(marker)) continue;
            if (offset + 2 > b.length) return null;
            int length = u16be(b, offset);
            if (length < 2 || (long) offset + length > b.length) return null;
            int payload = offset + 2;
            if (isFrame(marker)) {
                if (length < 8 || payload + 6 > b.length) return null;
                int componentCount = u8(b, payload + 5);
                if (componentCount < 1 || length != 8 + 3 * componentCount) return null;
                long height = u16be(b, payload + 1);
                long width = u16be(b, payload + 3);
                if (height > 0) return dimensions(width, height);
                zeroHeightWidth = width;
            } else if (marker == 0xdc && zeroHeightWidth >= 0) {
                if (length != 4) return null;
                return dimensions(zeroHeightWidth, u16be(b, payload));
            }
            offset += length;
            if (marker == 0xda) entropy = true;
        }
        return zeroHeightWidth < 0 ? null : dimensions(zeroHeightWidth, 0);
    }

    private static Size webp(byte[] b) {
        if (b.length < 20 || !ascii(b, 0, "RIFF") || !ascii(b, 8, "WEBP")) return null;
        long riffEnd = u32le(b, 4) + 8L;
        if (riffEnd > b.length || riffEnd < 12) return null;
        int offset = 12;
        while ((long) offset + 8 <= riffEnd) {
            long chunkSize = u32le(b, offset + 4);
            long payload = (long) offset + 8;
            long payloadEnd = payload + chunkSize;
            long paddedEnd = payloadEnd + (chunkSize & 1L);
            if (payloadEnd > riffEnd || paddedEnd > riffEnd) return null;
            if (ascii(b, offset, "VP8X")) {
                if (chunkSize != 10) return null;
                return dimensions(1 + u24le(b, (int) payload + 4), 1 + u24le(b, (int) payload + 7));
            }
            if (ascii(b, offset, "VP8L")) {
                if (chunkSize < 5 || u8(b, (int) payload) != 0x2f) return null;
                long packed = u32le(b, (int) payload + 1);
                if ((packed >>> 29) != 0) return null;
                return dimensions(1 + (packed & 0x3fff), 1 + ((packed >>> 14) & 0x3fff));
            }
            if (ascii(b, offset, "VP8 ")) {
                if (chunkSize < 10 || (u8(b, (int) payload) & 1) != 0) return null;
                if (u8(b, (int) payload + 3) != 0x9d || u8(b, (int) payload + 4) != 0x01 || u8(b, (int) payload + 5) != 0x2a) return null;
                return dimensions(u16le(b, (int) payload + 6) & 0x3fff, u16le(b, (int) payload + 8) & 0x3fff);
            }
            offset = (int) paddedEnd;
        }
        return null;
    }

    private static Size dimensions(long width, long height) {
        if (width < 0 || height < 0) return null;
        return new Size(width, height);
    }
    private static boolean isFrame(int marker) {
        return marker == 0xc0 || marker == 0xc1 || marker == 0xc2 || marker == 0xc3
                || marker == 0xc5 || marker == 0xc6 || marker == 0xc7 || marker == 0xc9
                || marker == 0xca || marker == 0xcb || marker == 0xcd || marker == 0xce || marker == 0xcf;
    }
    private static boolean isRestart(int marker) { return marker >= 0xd0 && marker <= 0xd7; }
    private static int u8(byte[] b, int p) { return p < 0 || p >= b.length ? -1 : b[p] & 255; }
    private static int u16be(byte[] b, int p) { return (u8(b, p) << 8) | u8(b, p + 1); }
    private static int u16le(byte[] b, int p) { return u8(b, p) | (u8(b, p + 1) << 8); }
    private static int u24le(byte[] b, int p) { return u8(b, p) | (u8(b, p + 1) << 8) | (u8(b, p + 2) << 16); }
    private static long u32be(byte[] b, int p) {
        return ((long) u8(b, p) << 24) | ((long) u8(b, p + 1) << 16) | ((long) u8(b, p + 2) << 8) | u8(b, p + 3);
    }
    private static long u32le(byte[] b, int p) {
        return u8(b, p) | ((long) u8(b, p + 1) << 8) | ((long) u8(b, p + 2) << 16) | ((long) u8(b, p + 3) << 24);
    }
    private static boolean ascii(byte[] b, int p, String text) {
        if (p < 0 || (long) p + text.length() > b.length) return false;
        for (int i = 0; i < text.length(); i++) if (u8(b, p + i) != text.charAt(i)) return false;
        return true;
    }
}
