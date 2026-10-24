package jp.igapyon.mikumd2html.core;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Per-conversion image validation, embedding, and accounting. */
public final class ImageEmbeddingSession {
    private final AssetResolver assets;
    private final List<Diagnostic> diagnostics;
    private final long maxAssetBytes;
    private int occurrences;
    private int embeddedOccurrences;
    private long sourceBytes;
    private long payloadBytes;
    private final Set<String> unique = new HashSet<String>();
    private final Set<String> warned = new HashSet<String>();
    private final Map<String, AssetInfo> infoByPath = new LinkedHashMap<String, AssetInfo>();
    private final Map<String, Long> lengthById = new HashMap<String, Long>();
    private final Map<String, byte[]> bytesById = new HashMap<String, byte[]>();

    public ImageEmbeddingSession(AssetResolver assets, List<Diagnostic> diagnostics, long maxAssetBytes) {
        this.assets = assets;
        this.diagnostics = diagnostics;
        this.maxAssetBytes = maxAssetBytes;
    }

    public String renderImage(String altText, String destination, String title, int sourceLine, int sourceColumn) {
        occurrences++;
        String label = "markdown";
        SourcePosition position = new SourcePosition(Math.max(1, sourceLine), Math.max(1, sourceColumn));
        try {
            byte[] bytes;
            String expectedMime = null;
            String id;
            String trimmed = destination.trim();
            if (trimmed.regionMatches(true, 0, "data:", 0, 5)) {
                DataImage data = decodeDataUri(trimmed);
                bytes = data.bytes;
                expectedMime = data.mime;
                id = "data:" + sha256(bytes);
            } else {
                if (trimmed.matches("(?is)^https?://.*") || trimmed.startsWith("//")) {
                    throw new AssetAccessException("E_REMOTE_IMAGE", "Remote images are not supported.");
                }
                String path = decodeLocalPath(trimmed);
                if (assets == null) throw new AssetAccessException("E_ASSET_RESOLVER_REQUIRED", "A local image requires an asset resolver.");
                AssetInfo info = infoByPath.get(path);
                if (info == null) {
                    info = assets.describe(path);
                    if (info != null) infoByPath.put(path, info);
                }
                if (info == null || info.getId() == null || info.getByteLength() < 0) throw new AssetAccessException("E_IMAGE_READ", "Image resolver returned invalid metadata.");
                id = info.getId();
                Long previousLength = lengthById.get(id);
                if (previousLength != null && previousLength.longValue() != info.getByteLength()) {
                    throw new AssetAccessException("E_ASSET_CHANGED", "Image resolver returned inconsistent sizes for the same asset.");
                }
                lengthById.put(id, info.getByteLength());
                label = info.getLabel() == null ? path : info.getLabel();
                if (!bytesById.containsKey(id)) {
                    bytes = assets.read(id, maxAssetBytes);
                    if (bytes == null || bytes.length != info.getByteLength()) throw new AssetAccessException("E_ASSET_CHANGED", "Image asset size changed after inspection.");
                    bytesById.put(id, bytes);
                } else bytes = bytesById.get(id);
            }
            ImageInspector.Info info = ImageInspector.inspect(bytes, expectedMime);
            embeddedOccurrences++;
            String actualId = expectedMime == null && id.startsWith("data:") ? "data:" + sha256(bytes) : id;
            if (unique.add(actualId)) sourceBytes += bytes.length;
            payloadBytes += ((bytes.length + 2L) / 3L) * 4L;
            if (bytes.length > 5L * 1_048_576L && warned.add(actualId + ":bytes")) {
                Diagnostics.add(diagnostics, "warning", "W_LARGE_IMAGE", "Image exceeds 5 MiB and is embedded at its original size.", label, position);
            }
            if (info.getWidth() > 0 && info.getHeight() > 0 && info.getWidth() > 40_000_000L / info.getHeight()
                    && warned.add(actualId + ":pixels")) {
                java.util.Map<String, Object> dimensions = new java.util.LinkedHashMap<String, Object>();
                dimensions.put("width", info.getWidth());
                dimensions.put("height", info.getHeight());
                Diagnostics.add(diagnostics, "warning", "W_LARGE_IMAGE_DIMENSIONS", "Image exceeds 40 million pixels.", label, position, dimensions);
            }
            StringBuilder html = new StringBuilder("<img src=\"data:").append(info.getMime()).append(";base64,")
                    .append(Base64.getEncoder().encodeToString(bytes)).append("\" alt=\"")
                    .append(HtmlEscape.attribute(InlineMarkdownParser.plainText(altText)));
            if (title != null && !title.isEmpty()) html.append("\" title=\"").append(HtmlEscape.attribute(title));
            return html.append("\">").toString();
        } catch (AssetAccessException ex) {
            Diagnostics.add(diagnostics, "error", ex.getCode(), ex.getMessage(), label, position);
            return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
        }
    }

    public int getOccurrences() { return occurrences; }
    public int getEmbeddedOccurrences() { return embeddedOccurrences; }
    public int getOmittedOccurrences() { return occurrences - embeddedOccurrences; }
    public int getUniqueAssets() { return unique.size(); }
    public long getUniqueSourceAssetBytes() { return sourceBytes; }
    public long getEmbeddedPayloadBytes() { return payloadBytes; }

    private static final class DataImage {
        final String mime; final byte[] bytes;
        DataImage(String mime, byte[] bytes) { this.mime = mime; this.bytes = bytes; }
    }

    private static DataImage decodeDataUri(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)^data:(image/(?:png|jpeg|gif|webp|svg\\+xml));base64,([A-Za-z0-9+/]*={0,2})$").matcher(value);
        if (!matcher.matches()) throw new AssetAccessException("E_IMAGE_URI", "Only Base64 data URLs for supported image types are accepted.");
        String mime = matcher.group(1).toLowerCase(Locale.ROOT);
        String encoded = matcher.group(2);
        if (encoded.isEmpty()) throw new AssetAccessException("E_IMAGE_INVALID", "The image data URL is empty or has invalid Base64.");
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (!Base64.getEncoder().encodeToString(bytes).equals(encoded)) throw new IllegalArgumentException("non-canonical Base64");
            return new DataImage(mime, bytes);
        } catch (IllegalArgumentException ex) {
            throw new AssetAccessException("E_IMAGE_INVALID", "The image data URL has invalid Base64.", ex);
        }
    }

    private static String decodeLocalPath(String source) {
        if (source.isEmpty() || source.indexOf('?') >= 0 || source.indexOf('#') >= 0 || source.indexOf('\\') >= 0
                || source.indexOf('\0') >= 0 || source.startsWith("/") || source.startsWith("//")
                || source.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) {
            throw new AssetAccessException("E_IMAGE_URI", "The image path is not a supported relative URL path.");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < source.length();) {
            if (source.charAt(i) == '%') {
                if (i + 2 >= source.length()) throw new AssetAccessException("E_IMAGE_URI", "The image path has invalid percent encoding.");
                int high = Character.digit(source.charAt(i + 1), 16), low = Character.digit(source.charAt(i + 2), 16);
                if (high < 0 || low < 0) throw new AssetAccessException("E_IMAGE_URI", "The image path has invalid percent encoding.");
                output.write((high << 4) | low); i += 3;
            } else {
                int end = source.indexOf('%', i);
                if (end < 0) end = source.length();
                byte[] chunk = source.substring(i, end).getBytes(StandardCharsets.UTF_8);
                output.write(chunk, 0, chunk.length); i = end;
            }
        }
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString();
            if (decoded.isEmpty() || decoded.startsWith("/") || decoded.startsWith("\\") || decoded.indexOf('\\') >= 0
                    || decoded.indexOf('\0') >= 0 || decoded.matches("(?i)^[a-z]:.*")) throw new AssetAccessException("E_IMAGE_URI", "The decoded image path is not a supported relative path.");
            return decoded;
        } catch (CharacterCodingException ex) {
            throw new AssetAccessException("E_IMAGE_URI", "The image path has invalid UTF-8 encoding.", ex);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text = new StringBuilder();
            for (byte b : digest) text.append(String.format(Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
