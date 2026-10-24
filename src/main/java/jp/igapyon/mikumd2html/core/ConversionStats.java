package jp.igapyon.mikumd2html.core;

public final class ConversionStats {
    private final int headingCount;
    private final int imageOccurrences;
    private final int uniqueAssets;
    private final long uniqueSourceAssetBytes;
    private final long embeddedPayloadBytes;
    private final int embeddedImageOccurrences;
    private final int referencedImageOccurrences;
    private final int omittedImageOccurrences;
    private final long htmlBytes;

    public ConversionStats(int headingCount, int imageOccurrences, int uniqueAssets,
            long uniqueSourceAssetBytes, long embeddedPayloadBytes, long htmlBytes) {
        this(headingCount, imageOccurrences, uniqueAssets, uniqueSourceAssetBytes, embeddedPayloadBytes,
                imageOccurrences, 0, 0, htmlBytes);
    }

    public ConversionStats(int headingCount, int imageOccurrences, int uniqueAssets,
            long uniqueSourceAssetBytes, long embeddedPayloadBytes, int embeddedImageOccurrences,
            int referencedImageOccurrences, int omittedImageOccurrences, long htmlBytes) {
        this.headingCount = headingCount;
        this.imageOccurrences = imageOccurrences;
        this.uniqueAssets = uniqueAssets;
        this.uniqueSourceAssetBytes = uniqueSourceAssetBytes;
        this.embeddedPayloadBytes = embeddedPayloadBytes;
        this.embeddedImageOccurrences = embeddedImageOccurrences;
        this.referencedImageOccurrences = referencedImageOccurrences;
        this.omittedImageOccurrences = omittedImageOccurrences;
        this.htmlBytes = htmlBytes;
    }

    public int getHeadingCount() { return headingCount; }
    public int getImageOccurrences() { return imageOccurrences; }
    public int getUniqueAssets() { return uniqueAssets; }
    public long getUniqueSourceAssetBytes() { return uniqueSourceAssetBytes; }
    public long getEmbeddedPayloadBytes() { return embeddedPayloadBytes; }
    public int getEmbeddedImageOccurrences() { return embeddedImageOccurrences; }
    public int getReferencedImageOccurrences() { return referencedImageOccurrences; }
    public int getOmittedImageOccurrences() { return omittedImageOccurrences; }
    public long getHtmlBytes() { return htmlBytes; }
}
