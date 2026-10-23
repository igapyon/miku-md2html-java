package jp.igapyon.mikumd2html.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Diagnostic {
    private final String severity;
    private final String code;
    private final String message;
    private final String label;
    private final SourcePosition start;
    private final Map<String, Object> details;

    public Diagnostic(String severity, String code, String message, String label, SourcePosition start, Map<String, Object> details) {
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.label = label;
        this.start = start;
        this.details = details == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(details));
    }

    public String getSeverity() { return severity; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getLabel() { return label; }
    public SourcePosition getStart() { return start; }
    public Map<String, Object> getDetails() { return details; }
}
