package jp.igapyon.mikumd2html.adapters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import jp.igapyon.mikumd2html.core.AssetAccessException;
import jp.igapyon.mikumd2html.core.AssetInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    @Test void rejectsTraversalOutsideTheAssetRoot() {
        LocalAssetResolver resolver = LocalAssetResolver.create(directory.resolve("guide.md"), null);
        AssetAccessException error = assertThrows(AssetAccessException.class, () -> resolver.describe("../outside.png"));
        assertEquals("E_ASSET_OUTSIDE_ROOT", error.getCode());
    }
}
