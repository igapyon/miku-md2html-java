package jp.igapyon.mikumd2html.core;

public final class AssetAccessException extends RuntimeException {
    private final String code;

    public AssetAccessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AssetAccessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() { return code; }
}
