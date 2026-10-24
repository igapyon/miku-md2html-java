package jp.igapyon.mikumd2html.adapters;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalAssetPathsTest {
    @Test void encodesEachPathSegmentLikeEncodeURIComponent() {
        assertEquals("../docs/images/%E5%9B%B3%20a%25%3F%23.png",
                LocalAssetPaths.relativeImageHref(Paths.get("/work/out/page.html"), Paths.get("/work/docs/images/図 a%?#.png")));
        assertEquals("./a%2B%20!'()*.png",
                LocalAssetPaths.relativeImageHref(Paths.get("/work/out/page.html"), Paths.get("/work/out/a+ !'()*.png")));
    }
}
