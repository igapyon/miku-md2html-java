package jp.igapyon.mikumd2html.core;

public final class ConversionStats {
    private final int headingCount;
    private final int imageOccurrences;
    private final int uniqueAssets;
    private final long uniqueSourceAssetBytes;
    private final long embeddedPayloadBytes;
    private final long htmlBytes;

    public ConversionStats(int headingCount, int imageOccurrences, int uniqueAssets,
            long uniqueSourceAssetBytes, long embeddedPayloadBytes, long htmlBytes) {
        this.headingCount = headingCount;
        this.imageOccurrences = imageOccurrences;
        this.uniqueAssets = uniqueAssets;
        this.uniqueSourceAssetBytes = uniqueSourceAssetBytes;
        this.embeddedPayloadBytes = embeddedPayloadBytes;
        this.htmlBytes = htmlBytes;
    }

    public int getHeadingCount() { return headingCount; }
    public int getImageOccurrences() { return imageOccurrences; }
    public int getUniqueAssets() { return uniqueAssets; }
    public long getUniqueSourceAssetBytes() { return uniqueSourceAssetBytes; }
    public long getEmbeddedPayloadBytes() { return embeddedPayloadBytes; }
    public long getHtmlBytes() { return htmlBytes; }
}
