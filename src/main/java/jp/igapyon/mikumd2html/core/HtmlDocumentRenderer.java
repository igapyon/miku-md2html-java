package jp.igapyon.mikumd2html.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class HtmlDocumentRenderer {
    private static final String ASSET_ROOT = "/jp/igapyon/mikumd2html/viewer/";

    public String render(String articleHtml, String toc, String title, String lang, String description) {
        String css = readResource("theme.css").trim();
        String script = "\n" + readResource("viewer.js").trim() + "\n";
        css = css.replaceAll("(?i)</style", "<\\\\/style");
        script = script.replaceAll("(?i)</script", "<\\\\/script");
        String hash = sha256(script);
        String csp = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'sha256-"
                + hash + "'; connect-src 'none'; font-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html lang=\"").append(HtmlEscape.attribute(lang)).append("\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(HtmlEscape.attribute(csp)).append("\">\n");
        if (description != null && !description.isEmpty()) html.append("<meta name=\"description\" content=\"").append(HtmlEscape.attribute(description)).append("\">\n");
        html.append("<title>").append(HtmlEscape.text(title)).append("</title>\n<style>\n").append(css)
                .append("\n</style>\n</head>\n<body>\n<main>\n");
        if (toc != null && !toc.isEmpty()) html.append(toc).append('\n');
        html.append("<article>\n").append(articleHtml).append("\n</article>\n</main>\n<script>")
                .append(script).append("</script>\n</body>\n</html>\n");
        return html.toString();
    }

    private static String readResource(String name) {
        InputStream input = HtmlDocumentRenderer.class.getResourceAsStream(ASSET_ROOT + name);
        if (input == null) throw new IllegalStateException("Bundled viewer asset is missing: " + name);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Bundled viewer asset could not be read: " + name, ex);
        } finally {
            try { input.close(); } catch (IOException ignored) { }
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
