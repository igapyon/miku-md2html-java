package jp.igapyon.mikumd2html.core;

/** Tracks decoded local image references independently from asset inspection and reads. */
public interface ImageReferenceTracker {
    void recordReference(String decodedRelativePath);

    void completeReferenceScan();

    boolean isReferenceScanComplete();
}
