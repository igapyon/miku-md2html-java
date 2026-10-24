package jp.igapyon.mikumd2html.core;

public interface AssetResolver {
    AssetInfo describe(String decodedRelativePath);
    byte[] read(String id, long maxBytes);

    /** Return a safe encoded relative URL for a local asset, or fail when unsupported. */
    default String reference(String decodedRelativePath) {
        throw new AssetAccessException("E_ASSET_REFERENCE_REQUIRED",
                "A local raster image requires an AssetResolver.reference implementation in reference mode.");
    }
}
