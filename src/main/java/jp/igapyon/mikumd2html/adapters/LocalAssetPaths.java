package jp.igapyon.mikumd2html.adapters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jp.igapyon.mikumd2html.core.AssetAccessException;

/** Filesystem path and URL helpers shared by the local resolver and CLI. */
public final class LocalAssetPaths {
    private LocalAssetPaths() { }

    public static boolean isWithin(Path root, Path candidate) {
        Path relative = root.relativize(candidate);
        if (relative.isAbsolute()) return false;
        if (relative.getNameCount() == 0 || relative.toString().isEmpty()) return true;
        String first = relative.getName(0).toString();
        return !"..".equals(first);
    }

    /** Resolve existing ancestors through symlinks while retaining a not-yet-created suffix. */
    public static Path canonicalPotentialPath(Path path) throws IOException {
        Path current = path.toAbsolutePath().normalize();
        List<Path> suffix = new ArrayList<Path>();
        while (true) {
            try {
                Path result = current.toRealPath();
                for (Path part : suffix) result = result.resolve(part);
                return result.normalize();
            } catch (NoSuchFileException | NotDirectoryException ex) {
                Path parent = current.getParent();
                if (parent == null || parent.equals(current)) throw ex;
                suffix.add(0, current.getFileName());
                current = parent;
            }
        }
    }

    public static String relativeImageHref(Path outputFile, Path imageFile) {
        try {
            Path outputDirectory = outputFile.toAbsolutePath().normalize().getParent();
            if (outputDirectory == null) throw referenceError("The local image cannot be expressed as a relative URL from the HTML output.", null);
            Path target = imageFile.toAbsolutePath().normalize();
            Path relative = outputDirectory.relativize(target);
            if (relative.isAbsolute() || relative.toString().isEmpty()) {
                throw referenceError("The local image cannot be expressed as a relative URL from the HTML output.", null);
            }
            StringBuilder href = new StringBuilder();
            for (Path segmentPath : relative) {
                if (href.length() > 0) href.append('/');
                String segment = segmentPath.toString();
                href.append("..".equals(segment) ? ".." : encodeURIComponent(segment));
            }
            String value = href.toString();
            return value.startsWith("../") || "..".equals(value) ? value : "./" + value;
        } catch (IllegalArgumentException ex) {
            throw referenceError("The local image cannot be expressed as a relative URL from the HTML output.", ex);
        }
    }

    private static String encodeURIComponent(String value) {
        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            StringBuilder result = new StringBuilder();
            while (bytes.hasRemaining()) {
                int b = bytes.get() & 255;
                if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9')
                        || b == '-' || b == '_' || b == '.' || b == '!' || b == '~' || b == '*' || b == '\'' || b == '(' || b == ')') {
                    result.append((char) b);
                } else {
                    result.append('%');
                    final char[] hex = "0123456789ABCDEF".toCharArray();
                    result.append(hex[b >>> 4]).append(hex[b & 15]);
                }
            }
            return result.toString();
        } catch (CharacterCodingException ex) {
            throw referenceError("The local image path cannot be encoded as a relative URL.", ex);
        }
    }

    private static AssetAccessException referenceError(String message, Throwable cause) {
        return cause == null ? new AssetAccessException("E_IMAGE_REFERENCE", message)
                : new AssetAccessException("E_IMAGE_REFERENCE", message, cause);
    }
}
