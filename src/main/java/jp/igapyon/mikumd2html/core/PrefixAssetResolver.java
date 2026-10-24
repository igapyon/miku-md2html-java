package jp.igapyon.mikumd2html.core;

/** Optional bounded header read supported by resolvers that can read prefixes efficiently. */
public interface PrefixAssetResolver extends AssetResolver {
    byte[] readPrefix(String id, long maxBytes);
}
