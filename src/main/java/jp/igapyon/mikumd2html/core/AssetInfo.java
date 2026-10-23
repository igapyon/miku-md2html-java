package jp.igapyon.mikumd2html.core;

public final class AssetInfo {
    private final String id;
    private final String label;
    private final long byteLength;

    public AssetInfo(String id, String label, long byteLength) {
        this.id = id;
        this.label = label;
        this.byteLength = byteLength;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public long getByteLength() { return byteLength; }
}
