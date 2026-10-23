package jp.igapyon.mikumd2html.adapters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.igapyon.mikumd2html.core.AssetAccessException;
import jp.igapyon.mikumd2html.core.AssetInfo;
import jp.igapyon.mikumd2html.core.AssetResolver;

/** Local filesystem adapter with both lexical and canonical root containment checks. */
public final class LocalAssetResolver implements AssetResolver {
    private static final class Record {
        final Path path; final long size; final Object fileKey; final long modified;
        Record(Path path, BasicFileAttributes attrs) { this.path = path; this.size = attrs.size(); this.fileKey = attrs.fileKey(); this.modified = attrs.lastModifiedTime().toMillis(); }
    }

    private final Path root;
    private final Path base;
    private final Map<String, Record> records = new HashMap<String, Record>();
    private final java.util.Set<Path> referencedCandidates = new java.util.LinkedHashSet<Path>();

    private LocalAssetResolver(Path root, Path base) { this.root = root; this.base = base; }

    public static LocalAssetResolver create(Path markdownFile, Path assetRoot) {
        try {
            Path markdown = markdownFile.toAbsolutePath().normalize();
            Path rootCandidate = assetRoot == null ? markdown.getParent() : assetRoot.toAbsolutePath().normalize();
            if (rootCandidate == null || !Files.isDirectory(rootCandidate)) throw new IOException("asset root is not a directory");
            Path root = rootCandidate.toRealPath();
            Path base = markdown.getParent().toRealPath();
            return new LocalAssetResolver(root, base);
        } catch (IOException ex) {
            throw new AssetAccessException("E_IMAGE_READ", "Asset root is not a readable directory.", ex);
        }
    }

    @Override public synchronized AssetInfo describe(String decodedRelativePath) {
        try {
            Path relative = Paths.get(decodedRelativePath);
            if (relative.isAbsolute()) throw new AssetAccessException("E_IMAGE_URI", "The image path is not a supported relative URL path.");
            Path candidate = base.resolve(relative).normalize();
            referencedCandidates.add(candidate);
            if (!candidate.startsWith(root)) throw new AssetAccessException("E_ASSET_OUTSIDE_ROOT", "Image asset resolves outside the configured asset root: " + decodedRelativePath);
            Path canonical = candidate.toRealPath();
            if (!canonical.startsWith(root)) throw new AssetAccessException("E_ASSET_OUTSIDE_ROOT", "Image asset resolves outside the configured asset root: " + decodedRelativePath);
            BasicFileAttributes attrs = Files.readAttributes(canonical, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile() || attrs.size() < 0) throw new AssetAccessException("E_IMAGE_READ", "Image asset is not a regular file: " + decodedRelativePath);
            String id = canonical.toString();
            records.put(id, new Record(canonical, attrs));
            return new AssetInfo(id, root.relativize(canonical).toString().replace(canonical.getFileSystem().getSeparator(), "/"), attrs.size());
        } catch (AssetAccessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new AssetAccessException("E_IMAGE_NOT_FOUND", "Image asset could not be inspected: " + decodedRelativePath, ex);
        }
    }

    @Override public synchronized byte[] read(String id, long maxBytes) {
        Record record = records.get(id);
        if (record == null) throw new AssetAccessException("E_ASSET_CHANGED", "Image asset was not described before reading.");
        try {
            Path current = record.path.toRealPath();
            BasicFileAttributes before = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!current.startsWith(root) || !same(record, before)) throw new AssetAccessException("E_ASSET_CHANGED", "Image asset changed after inspection.");
            if (record.size > maxBytes) throw new AssetAccessException("E_OUTPUT_TOO_LARGE", "Image asset exceeds the configured output limit.");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(record.size, 65536L));
            try (InputStream input = Files.newInputStream(current)) {
                byte[] buffer = new byte[32768];
                int count;
                long total = 0;
                while ((count = input.read(buffer)) >= 0) {
                    total += count;
                    if (total > maxBytes || total > record.size) throw new AssetAccessException("E_OUTPUT_TOO_LARGE", "Image asset exceeded the bounded read limit.");
                    bytes.write(buffer, 0, count);
                }
            }
            BasicFileAttributes after = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (bytes.size() != record.size || !same(record, after)) throw new AssetAccessException("E_ASSET_CHANGED", "Image asset changed while it was being read.");
            return bytes.toByteArray();
        } catch (AssetAccessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AssetAccessException("E_IMAGE_READ", "Image asset could not be read.", ex);
        }
    }

    private static boolean same(Record record, BasicFileAttributes attrs) {
        return attrs.isRegularFile() && attrs.size() == record.size && attrs.lastModifiedTime().toMillis() == record.modified
                && (record.fileKey == null || record.fileKey.equals(attrs.fileKey()));
    }

    public synchronized List<Path> getAssetPaths() {
        List<Path> paths = new ArrayList<Path>();
        for (Record record : records.values()) paths.add(record.path);
        return paths;
    }

    public synchronized List<Path> getReferencedPaths() {
        return new ArrayList<Path>(referencedCandidates);
    }
}
