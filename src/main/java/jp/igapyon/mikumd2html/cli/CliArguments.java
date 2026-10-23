package jp.igapyon.mikumd2html.cli;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CliArguments {
    private static final Set<String> VALUE_OPTIONS = new HashSet<String>();
    private static final Set<String> BOOLEAN_OPTIONS = new HashSet<String>();
    static {
        VALUE_OPTIONS.add("--out"); VALUE_OPTIONS.add("--title"); VALUE_OPTIONS.add("--lang");
        VALUE_OPTIONS.add("--asset-root"); VALUE_OPTIONS.add("--max-output-mib"); VALUE_OPTIONS.add("--report-json");
        BOOLEAN_OPTIONS.add("--no-toc"); BOOLEAN_OPTIONS.add("--force");
    }
    private CliArguments() { }

    public static CliOptions parse(String[] args) throws CliUsageException {
        if (args.length == 0) throw new CliUsageException("No input file was provided.");
        Map<String, String> values = new HashMap<String, String>();
        Set<String> flags = new HashSet<String>();
        List<String> positional = new ArrayList<String>();
        boolean parseOptions = true;
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (parseOptions && "--".equals(token)) { parseOptions = false; continue; }
            if (!parseOptions || !token.startsWith("--")) { positional.add(token); continue; }
            int equals = token.indexOf('=');
            String key = equals < 0 ? token : token.substring(0, equals);
            String inlineValue = equals < 0 ? null : token.substring(equals + 1);
            if (!VALUE_OPTIONS.contains(key) && !BOOLEAN_OPTIONS.contains(key)) throw new CliUsageException("Unknown option: " + key);
            if (values.containsKey(key) || flags.contains(key)) throw new CliUsageException("Option may only be specified once: " + key);
            if (BOOLEAN_OPTIONS.contains(key)) {
                if (inlineValue != null) throw new CliUsageException("Boolean option does not accept a value: " + key);
                flags.add(key); continue;
            }
            String value = inlineValue;
            if (value == null) {
                if (i + 1 >= args.length || args[i + 1].startsWith("-")) throw new CliUsageException("Missing value for " + key);
                value = args[++i];
            }
            if (value.trim().isEmpty()) throw new CliUsageException("Value for " + key + " must not be empty.");
            values.put(key, value);
        }
        if (positional.size() != 1) throw new CliUsageException(positional.isEmpty() ? "No input file was provided." : "Provide exactly one input file.");
        String input = positional.get(0);
        String output = values.get("--out");
        if (output == null) throw new CliUsageException("Required option --out is missing.");
        if (!input.matches("(?i).*\\.(md|markdown)$")) throw new CliUsageException("Input must end in .md or .markdown.");
        if (!output.matches("(?i).*\\.html$")) throw new CliUsageException("Output path must end in .html.");
        String title = values.get("--title");
        if (title != null && title.trim().isEmpty()) throw new CliUsageException("--title must not be blank.");
        String lang = values.get("--lang");
        if (lang != null && !lang.trim().matches("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")) throw new CliUsageException("--lang must be an ASCII language tag such as ja or en-US.");
        String maxText = values.containsKey("--max-output-mib") ? values.get("--max-output-mib") : "100";
        if (!maxText.matches("[0-9]+")) throw new CliUsageException("--max-output-mib must be an integer from 1 through 1024.");
        long mib;
        try { mib = Long.parseLong(maxText); } catch (NumberFormatException ex) { throw new CliUsageException("--max-output-mib must be an integer from 1 through 1024."); }
        if (mib < 1 || mib > 1024) throw new CliUsageException("--max-output-mib must be an integer from 1 through 1024.");
        return new CliOptions(Paths.get(input), Paths.get(output), title == null ? null : title.trim(), lang == null ? null : lang.trim(),
                values.containsKey("--asset-root") ? Paths.get(values.get("--asset-root")) : null, flags.contains("--no-toc"),
                mib * 1_048_576L, values.containsKey("--report-json") ? Paths.get(values.get("--report-json")) : null, flags.contains("--force"));
    }
}
