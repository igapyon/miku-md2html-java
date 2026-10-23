package jp.igapyon.mikumd2html.core;

public interface AssetResolver {
    AssetInfo describe(String decodedRelativePath);
    byte[] read(String id, long maxBytes);
}
