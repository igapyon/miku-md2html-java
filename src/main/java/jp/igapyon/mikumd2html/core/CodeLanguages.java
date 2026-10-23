package jp.igapyon.mikumd2html.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class CodeLanguages {
    private static final Map<String, String> ALIASES;
    static {
        Map<String, String> aliases = new HashMap<String, String>();
        add(aliases, "markdown", "markdown", "md");
        add(aliases, "java", "java");
        add(aliases, "javascript", "javascript", "js", "ecmascript", "node", "nodejs");
        add(aliases, "typescript", "typescript", "ts");
        add(aliases, "python", "python", "py", "python3");
        add(aliases, "css", "css");
        add(aliases, "html", "html", "htm");
        add(aliases, "xml", "xml", "svg");
        add(aliases, "json", "json");
        add(aliases, "bash", "shell", "sh", "bash");
        add(aliases, "yaml", "yaml", "yml");
        add(aliases, "diff", "diff", "patch");
        add(aliases, "powershell", "powershell", "pwsh", "ps1");
        ALIASES = Collections.unmodifiableMap(aliases);
    }

    private CodeLanguages() { }

    private static void add(Map<String, String> aliases, String canonical, String... names) {
        for (String name : names) aliases.put(name, canonical);
    }

    public static String resolve(String info) {
        if (info == null || info.trim().isEmpty()) return null;
        String token = info.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return ALIASES.get(token);
    }
}
