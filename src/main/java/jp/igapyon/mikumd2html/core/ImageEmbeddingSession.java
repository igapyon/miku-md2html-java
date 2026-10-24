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
    private static final long REFERENCE_INSPECTION_LIMIT = 100L * 1_048_576L;
    private static final int IMAGE_PREFIX_BYTES = 256;
    private final AssetResolver assets;
    private final List<Diagnostic> diagnostics;
    private final long maxAssetBytes;
    private final String imageMode;
    private int occurrences;
    private int embeddedOccurrences;
    private int referencedOccurrences;
    private long sourceBytes;
    private long payloadBytes;
    private final Set<String> unique = new HashSet<String>();
    private final Set<String> warned = new HashSet<String>();
    private final Map<String, AssetInfo> infoByPath = new LinkedHashMap<String, AssetInfo>();
    private final Map<String, Long> lengthById = new HashMap<String, Long>();
    private final Map<String, byte[]> bytesById = new HashMap<String, byte[]>();
    private final Map<String, String> referencesByPath = new HashMap<String, String>();

    public ImageEmbeddingSession(AssetResolver assets, List<Diagnostic> diagnostics, long maxAssetBytes) {
        this(assets, diagnostics, maxAssetBytes, "embed");
    }

    public ImageEmbeddingSession(AssetResolver assets, List<Diagnostic> diagnostics, long maxAssetBytes, String imageMode) {
        this.assets = assets;
        this.diagnostics = diagnostics;
        this.maxAssetBytes = maxAssetBytes;
        this.imageMode = imageMode;
    }

    public String renderImage(String altText, String destination, String title, int sourceLine, int sourceColumn) {
        occurrences++;
        String label = "markdown";
        boolean localSource = false;
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
                localSource = true;
                if (trimmed.matches("(?is)^https?://.*") || trimmed.startsWith("//")) {
                    throw new AssetAccessException("E_REMOTE_IMAGE", "Remote images are not supported.");
                }
                String path = decodeLocalPath(trimmed);
                if (assets == null) throw new AssetAccessException("E_ASSET_RESOLVER_REQUIRED", "A local image requires an asset resolver.");
                AssetInfo info = infoByPath.get(path);
                if (info == null) {
                    try {
                        info = assets.describe(path);
                    } catch (AssetAccessException ex) {
                        if (!"reference".equals(imageMode) || !warnLocalError(ex, path, "markdown", position)) throw ex;
                        if ("W_ASSET_OUTSIDE_ROOT".equals(mappedWarning(ex.getCode())) || path.toLowerCase(Locale.ROOT).endsWith(".svg")) {
                            return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
                        }
                        return makeReference(path, altText, position);
                    }
                    if (info != null) infoByPath.put(path, info);
                }
                if (info == null) throw new IllegalStateException("AssetResolver.describe returned invalid metadata.");
                if (info.getId() == null || info.getByteLength() < 0) throw new IllegalStateException("AssetResolver.describe returned invalid metadata.");
                id = info.getId();
                label = info.getLabel() == null ? path : info.getLabel();
                if (info.getId().isEmpty() || info.getLabel() == null || info.getByteLength() > 9_007_199_254_740_991L) {
                    throw new IllegalStateException("AssetResolver.describe returned invalid metadata.");
                }
                Long knownSize = lengthById.get(id);
                if (knownSize != null && knownSize.longValue() != info.getByteLength()) {
                    throw new IllegalStateException("AssetResolver.describe returned inconsistent sizes for one asset id.");
                }
                if (knownSize == null) sourceBytes += info.getByteLength();
                unique.add(id);
                lengthById.put(id, info.getByteLength());
                if ("reference".equals(imageMode)) return renderLocalReference(altText, path, info, label, position);
                if (!bytesById.containsKey(id)) {
                    try {
                        bytes = assets.read(id, maxAssetBytes);
                    } catch (AssetAccessException ex) {
                        if (warnLocalError(ex, id, "markdown", position)) return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
                        throw ex;
                    }
                    if (bytes == null || bytes.length != info.getByteLength()) throw new AssetAccessException("E_ASSET_CHANGED", "Image asset size changed after inspection.");
                    bytesById.put(id, bytes);
                } else bytes = bytesById.get(id);
            }
            final ImageInspector.Info info;
            try {
                info = ImageInspector.inspect(bytes, expectedMime);
            } catch (AssetAccessException ex) {
                if (localSource && warnLocalError(ex, id, "markdown", position)) return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
                throw ex;
            }
            embeddedOccurrences++;
            String actualId = expectedMime == null && id.startsWith("data:") ? "embedded:" + info.getMime() + ":" + sha256(bytes) : id;
            if (unique.add(actualId)) sourceBytes += bytes.length;
            payloadBytes += ((bytes.length + 2L) / 3L) * 4L;
            warnLarge(actualId, bytes.length, info.getWidth(), info.getHeight(), label, position, true);
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
            Diagnostics.add(diagnostics, "error", ex.getCode(), ex.getMessage(), "markdown", position);
            return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
        }
    }

    public int getOccurrences() { return occurrences; }
    public int getEmbeddedOccurrences() { return embeddedOccurrences; }
    public int getReferencedOccurrences() { return referencedOccurrences; }
    public int getOmittedOccurrences() { return occurrences - embeddedOccurrences - referencedOccurrences; }
    public int getUniqueAssets() { return unique.size(); }
    public long getUniqueSourceAssetBytes() { return sourceBytes; }
    public long getEmbeddedPayloadBytes() { return payloadBytes; }

    private static final class DataImage {
        final String mime; final byte[] bytes;
        DataImage(String mime, byte[] bytes) { this.mime = mime; this.bytes = bytes; }
    }

    private String renderLocalReference(String altText, String path, AssetInfo metadata, String label, SourcePosition position) {
        boolean svgHint = path.toLowerCase(Locale.ROOT).endsWith(".svg");
        ImageInspector.Kind kind = new ImageInspector.Kind("unknown", null);
        byte[] prefix = null;
        try {
            if (!(assets instanceof PrefixAssetResolver) && metadata.getByteLength() > REFERENCE_INSPECTION_LIMIT && !svgHint) {
                warnInspection(metadata.getId(), metadata.getByteLength(), "Image exceeds the 100 MiB inspection limit and the resolver cannot read a bounded prefix.", position);
                return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
            }
            if (assets instanceof PrefixAssetResolver) {
                prefix = ((PrefixAssetResolver) assets).readPrefix(metadata.getId(), IMAGE_PREFIX_BYTES);
                int expected = (int) Math.min((long) IMAGE_PREFIX_BYTES, metadata.getByteLength());
                if (prefix == null || prefix.length != expected) throw new IllegalStateException("AssetResolver.readPrefix must return exactly the requested prefix length.");
            } else if (metadata.getByteLength() <= REFERENCE_INSPECTION_LIMIT) {
                byte[] all = readAsset(metadata.getId(), REFERENCE_INSPECTION_LIMIT, metadata.getByteLength());
                bytesById.put(metadata.getId(), all);
                prefix = java.util.Arrays.copyOf(all, (int) Math.min((long) IMAGE_PREFIX_BYTES, all.length));
            } else {
                // SVG stays embedded, so its whole read is governed by the HTML output cap.
                byte[] all = readAsset(metadata.getId(), maxAssetBytes, metadata.getByteLength());
                bytesById.put(metadata.getId(), all);
                prefix = java.util.Arrays.copyOf(all, (int) Math.min((long) IMAGE_PREFIX_BYTES, all.length));
            }
            kind = ImageInspector.sniffImageKind(prefix);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (AssetAccessException ex) {
            if (ex.getCode().startsWith("E_SVG_")) throw ex;
            if ("E_OUTPUT_TOO_LARGE".equals(ex.getCode()) && metadata.getByteLength() > REFERENCE_INSPECTION_LIMIT && !svgHint) {
                warnInspection(metadata.getId(), metadata.getByteLength(), "Image exceeds the 100 MiB inspection limit; its complete contents were not read.", position);
                return kind.getKind().equals("raster") ? makeReference(path, altText, position) : HtmlEscape.text(InlineMarkdownParser.plainText(altText));
            }
            if (warnLocalError(ex, metadata.getId(), label, position)) {
                if ("W_ASSET_OUTSIDE_ROOT".equals(mappedWarning(ex.getCode())) || svgHint) return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
                return makeReference(path, altText, position);
            }
            Diagnostics.add(diagnostics, "error", ex.getCode(), ex.getMessage(), "markdown", position);
            return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
        }

        if (kind.getKind().equals("raster") && metadata.getByteLength() > REFERENCE_INSPECTION_LIMIT) {
            warnLarge(metadata.getId(), metadata.getByteLength(), 0L, 0L, "markdown", position, false);
            warnInspection(metadata.getId(), metadata.getByteLength(), "Image exceeds the 100 MiB inspection limit; only its format header was checked.", position);
            return makeReference(path, altText, position);
        }
        if (kind.getKind().equals("unknown") && metadata.getByteLength() > REFERENCE_INSPECTION_LIMIT && !svgHint) {
            warnInspection(metadata.getId(), metadata.getByteLength(), "Image exceeds the 100 MiB inspection limit and its format could not be identified.", position);
            return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
        }

        byte[] bytes;
        boolean svgCandidate = "svg".equals(kind.getKind()) || svgHint;
        long readLimit = svgCandidate ? maxAssetBytes : REFERENCE_INSPECTION_LIMIT;
        try {
            bytes = bytesById.get(metadata.getId());
            if (bytes == null) {
                bytes = readAsset(metadata.getId(), readLimit, metadata.getByteLength());
                bytesById.put(metadata.getId(), bytes);
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (AssetAccessException ex) {
            if (ex.getCode().startsWith("E_SVG_")) throw ex;
            if ("E_OUTPUT_TOO_LARGE".equals(ex.getCode()) && !svgCandidate) {
                warnInspection(metadata.getId(), metadata.getByteLength(), "Image exceeds the 100 MiB inspection limit; its complete contents were not read.", position);
                return "raster".equals(kind.getKind()) ? makeReference(path, altText, position) : HtmlEscape.text(InlineMarkdownParser.plainText(altText));
            }
            if (warnLocalError(ex, metadata.getId(), label, position)) {
                if ("W_ASSET_OUTSIDE_ROOT".equals(mappedWarning(ex.getCode())) || svgHint || "svg".equals(kind.getKind())) return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
                return makeReference(path, altText, position);
            }
            Diagnostics.add(diagnostics, "error", ex.getCode(), ex.getMessage(), "markdown", position);
            return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
        }
        if (bytes.length != metadata.getByteLength()) {
            warnLocalError(new AssetAccessException("E_ASSET_CHANGED", "changed"), metadata.getId(), "markdown", position);
            return (!svgHint && "raster".equals(kind.getKind())) ? makeReference(path, altText, position)
                    : HtmlEscape.text(InlineMarkdownParser.plainText(altText));
        }

        final ImageInspector.Info inspected;
        try {
            inspected = ImageInspector.inspect(bytes, null);
        } catch (AssetAccessException ex) {
            if (ex.getCode().startsWith("E_SVG_")) {
                Diagnostics.add(diagnostics, "error", ex.getCode(), ex.getMessage(), "markdown", position);
                return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
            }
            if (warnLocalError(ex, metadata.getId(), "markdown", position)) {
                return ("raster".equals(kind.getKind()) || (!svgHint && "unknown".equals(kind.getKind())))
                        ? makeReference(path, altText, position) : HtmlEscape.text(InlineMarkdownParser.plainText(altText));
            }
            Diagnostics.add(diagnostics, "error", ex.getCode(), ex.getMessage(), "markdown", position);
            return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
        }
        warnLarge(metadata.getId(), metadata.getByteLength(), inspected.getWidth(), inspected.getHeight(), "markdown", position, inspected.getMime().equals("image/svg+xml"));
        if ("image/svg+xml".equals(inspected.getMime())) {
            embeddedOccurrences++;
            payloadBytes += ((bytes.length + 2L) / 3L) * 4L;
            return imageHtml("data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(bytes), altText, null);
        }
        return makeReference(path, altText, position);
    }

    private byte[] readAsset(String id, long limit, long expectedLength) {
        byte[] bytes = bytesById.get(id);
        if (bytes == null) bytes = assets.read(id, limit);
        if (bytes == null) throw new IllegalStateException("AssetResolver.read must return bytes.");
        if (bytes.length > limit) throw new AssetAccessException("E_OUTPUT_TOO_LARGE", "Image asset exceeded the bounded read limit.");
        if (bytes.length != expectedLength) throw new AssetAccessException("E_ASSET_CHANGED", "Image asset size changed after inspection.");
        return bytes;
    }

    private String makeReference(String path, String altText, SourcePosition position) {
        String href = referencesByPath.get(path);
        if (href == null) {
            try {
                href = assets.reference(path);
                if (href == null || !href.startsWith("./") && !href.startsWith("../") || href.matches("(?s).*[^\\x20-\\x7e].*")) {
                    throw new IllegalStateException("AssetResolver.reference must return an encoded relative URL beginning with ./ or ../.");
                }
                decodeLocalPath(href);
                referencesByPath.put(path, href);
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (AssetAccessException ex) {
                if (warnLocalError(ex, path, "markdown", position)) return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
                Diagnostics.add(diagnostics, "error", ex.getCode(), ex.getMessage(), "markdown", position);
                return HtmlEscape.text(InlineMarkdownParser.plainText(altText));
            }
        }
        referencedOccurrences++;
        return imageHtml(href, altText, null);
    }

    private String imageHtml(String src, String altText, String title) {
        StringBuilder html = new StringBuilder("<img src=\"").append(HtmlEscape.attribute(src)).append("\" alt=\"")
                .append(HtmlEscape.attribute(InlineMarkdownParser.plainText(altText))).append('"');
        if (title != null && !title.isEmpty()) html.append(" title=\"").append(HtmlEscape.attribute(title)).append('"');
        return html.append('>').toString();
    }

    private void warnLarge(String id, long bytes, long width, long height, String label, SourcePosition position, boolean embedded) {
        if (bytes > 5L * 1_048_576L && warned.add(id + ":bytes")) {
            Diagnostics.add(diagnostics, "warning", "W_LARGE_IMAGE", embedded
                    ? "Image exceeds 5 MiB and is embedded at its original size."
                    : "Image exceeds 5 MiB and is referenced from its original file.", label, position,
                    java.util.Collections.<String, Object>singletonMap("bytes", bytes));
        }
        if (width > 0 && height > 0 && width > 40_000_000L / height && warned.add(id + ":pixels")) {
            java.util.Map<String, Object> details = new java.util.LinkedHashMap<String, Object>();
            details.put("width", width); details.put("height", height);
            Diagnostics.add(diagnostics, "warning", "W_LARGE_IMAGE_DIMENSIONS", "Image exceeds 40 million pixels.", label, position, details);
        }
    }

    private void warnInspection(String key, long bytes, String message, SourcePosition position) {
        if (warned.add(key + ":inspection")) {
            Diagnostics.add(diagnostics, "warning", "W_IMAGE_INSPECTION_SKIPPED", message, "markdown", position,
                    java.util.Collections.<String, Object>singletonMap("bytes", bytes));
        }
    }

    private boolean warnLocalError(AssetAccessException error, String key, String label, SourcePosition position) {
        String code = mappedWarning(error.getCode());
        if (code == null) return false;
        if (warned.add(key + ":" + code)) {
            String message;
            if ("W_IMAGE_NOT_FOUND".equals(code)) message = "A referenced local image file was not found.";
            else if ("W_IMAGE_READ".equals(code)) message = "A referenced local image file could not be read.";
            else if ("W_IMAGE_FORMAT".equals(code)) message = "A local image has an unsupported or unrecognized format.";
            else if ("W_IMAGE_INVALID".equals(code)) message = "A local image appears to be invalid or truncated.";
            else if ("W_ASSET_OUTSIDE_ROOT".equals(code)) message = "A local image resolves outside the configured asset root and was omitted.";
            else if ("W_ASSET_CHANGED".equals(code)) message = "A local image changed during inspection.";
            else message = "A safe relative URL could not be created for a local image.";
            Diagnostics.add(diagnostics, "warning", code, message, "markdown", position);
        }
        return true;
    }

    private static String mappedWarning(String code) {
        if ("E_IMAGE_NOT_FOUND".equals(code)) return "W_IMAGE_NOT_FOUND";
        if ("E_IMAGE_READ".equals(code)) return "W_IMAGE_READ";
        if ("E_IMAGE_FORMAT".equals(code)) return "W_IMAGE_FORMAT";
        if ("E_IMAGE_INVALID".equals(code)) return "W_IMAGE_INVALID";
        if ("E_ASSET_OUTSIDE_ROOT".equals(code)) return "W_ASSET_OUTSIDE_ROOT";
        if ("E_ASSET_CHANGED".equals(code)) return "W_ASSET_CHANGED";
        if ("E_IMAGE_REFERENCE".equals(code)) return "W_IMAGE_REFERENCE";
        return null;
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
