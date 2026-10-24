package jp.igapyon.mikumd2html.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class Diagnostics {
    private Diagnostics() { }

    public static void add(List<Diagnostic> diagnostics, String severity, String code, String message,
            String label, SourcePosition start) {
        diagnostics.add(new Diagnostic(severity, code, message, label, start, null));
    }

    public static void add(List<Diagnostic> diagnostics, String severity, String code, String message,
            String label, SourcePosition start, java.util.Map<String, Object> details) {
        diagnostics.add(new Diagnostic(severity, code, message, label, start, details));
    }

    public static boolean hasErrors(List<Diagnostic> diagnostics) {
        for (Diagnostic item : diagnostics) if ("error".equals(item.getSeverity())) return true;
        return false;
    }

    public static List<Diagnostic> stable(List<Diagnostic> diagnostics) {
        List<Diagnostic> result = new ArrayList<Diagnostic>(diagnostics);
        Collections.sort(result, new Comparator<Diagnostic>() {
            @Override public int compare(Diagnostic left, Diagnostic right) {
                SourcePosition a = left.getStart();
                SourcePosition b = right.getStart();
                if (a != null && b != null) {
                    if (a.getLine() != b.getLine()) return a.getLine() < b.getLine() ? -1 : 1;
                    if (a.getColumn() != b.getColumn()) return a.getColumn() < b.getColumn() ? -1 : 1;
                } else if (a != null || b != null) {
                    return a != null ? -1 : 1;
                }
                return left.getCode().compareTo(right.getCode());
            }
        });
        return result;
    }
}
