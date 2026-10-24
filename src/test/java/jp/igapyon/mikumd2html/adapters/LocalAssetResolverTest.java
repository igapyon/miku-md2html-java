package jp.igapyon.mikumd2html.adapters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Base64;
import jp.igapyon.mikumd2html.core.AssetAccessException;
import jp.igapyon.mikumd2html.core.AssetInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.*;

class LocalAssetResolverTest {
    @TempDir Path directory;

    @Test void describesAndReadsAnImageInsideTheMarkdownDirectory() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/odcAAAAASUVORK5CYII=");
        Files.write(directory.resolve("pixel.png"), png);
        LocalAssetResolver resolver = LocalAssetResolver.create(directory.resolve("guide.md"), null);
        AssetInfo info = resolver.describe("pixel.png");
        assertEquals(png.length, info.getByteLength());
        assertArrayEquals(png, resolver.read(info.getId(), png.length));
    }

    @Test void rejectsTraversalOutsideTheAssetRoot() throws Exception {
        LocalAssetResolver resolver = LocalAssetResolver.create(directory.resolve("guide.md"), null);
        AssetAccessException error = assertThrows(AssetAccessException.class, () -> resolver.describe("../outside.png"));
        assertEquals("E_ASSET_OUTSIDE_ROOT", error.getCode());
        assertEquals(directory.toRealPath().getParent().resolve("outside.png").normalize(), resolver.getReferencedPaths().get(0));
    }

    @Test void retainsMissingCandidatePathsForOutputCollisionChecks() throws Exception {
        LocalAssetResolver resolver = LocalAssetResolver.create(directory.resolve("guide.md"), null);
        AssetAccessException error = assertThrows(AssetAccessException.class, () -> resolver.describe("missing.png"));
        assertEquals("E_IMAGE_NOT_FOUND", error.getCode());
        assertEquals(directory.toRealPath().resolve("missing.png").normalize(), resolver.getReferencedPaths().get(0));
    }

    @Test void detectsSubMillisecondModificationWhenTheFilesystemPreservesIt() throws Exception {
        Path image = directory.resolve("pixel.png");
        Files.write(image, new byte[] {1, 2, 3});
        LocalAssetResolver resolver = LocalAssetResolver.create(directory.resolve("guide.md"), null);
        AssetInfo info = resolver.describe("pixel.png");

        FileTime original = Files.getLastModifiedTime(image);
        Instant changedInstant = Instant.ofEpochMilli(original.toMillis()).plusNanos(1L);
        FileTime changed = FileTime.from(changedInstant);
        Files.setLastModifiedTime(image, changed);
        FileTime observed = Files.getLastModifiedTime(image);
        Assumptions.assumeTrue(changed.equals(observed) && original.toMillis() == observed.toMillis(),
                "filesystem does not preserve a sub-millisecond mtime change");

        AssetAccessException error = assertThrows(AssetAccessException.class, () -> resolver.read(info.getId(), 100L));
        assertEquals("E_ASSET_CHANGED", error.getCode());
    }
}
