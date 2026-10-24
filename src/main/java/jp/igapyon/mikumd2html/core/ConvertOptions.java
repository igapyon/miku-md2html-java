package jp.igapyon.mikumd2html.core;

public final class ConvertOptions {
    private String title;
    private String lang;
    private boolean toc = true;
    private long maxOutputBytes = 100L * 1024L * 1024L;
    private AssetResolver assets;
    private String imageMode = "reference";

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public boolean isToc() { return toc; }
    public void setToc(boolean toc) { this.toc = toc; }
    public long getMaxOutputBytes() { return maxOutputBytes; }
    public void setMaxOutputBytes(long maxOutputBytes) { this.maxOutputBytes = maxOutputBytes; }
    public AssetResolver getAssets() { return assets; }
    public void setAssets(AssetResolver assets) { this.assets = assets; }
    public String getImageMode() { return imageMode; }
    public void setImageMode(String imageMode) { this.imageMode = imageMode; }
}
