package jp.igapyon.mikumd2html.core;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class Slug {
    private Slug() { }

    public static String slugify(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        boolean pendingHyphen = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingHyphen = result.length() > 0;
                continue;
            }
            int type = Character.getType(codePoint);
            boolean keep = Character.isLetterOrDigit(codePoint)
                    || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || codePoint == '_' || codePoint == '-';
            if (!keep) continue;
            if (pendingHyphen && result.length() > 0 && result.charAt(result.length() - 1) != '-') result.append('-');
            pendingHyphen = false;
            result.appendCodePoint(codePoint);
        }
        while (result.length() > 0 && result.charAt(result.length() - 1) == '-') result.setLength(result.length() - 1);
        return result.length() == 0 ? "section" : result.toString();
    }

    public static String[] uniqueHeadingIds(String[] slugs) {
        Set<String> used = new HashSet<String>();
        String[] ids = new String[slugs.length];
        for (int i = 0; i < slugs.length; i++) {
            String base = "miku-h-" + slugs[i];
            String candidate = base;
            int suffix = 2;
            while (!used.add(candidate)) candidate = base + "-" + suffix++;
            ids[i] = candidate;
        }
        return ids;
    }
}
