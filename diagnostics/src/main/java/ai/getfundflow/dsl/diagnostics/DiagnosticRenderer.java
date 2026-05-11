package ai.getfundflow.dsl.diagnostics;

import ai.getfundflow.dsl.semantic.Diagnostic;
import ai.getfundflow.dsl.semantic.SourceLocation;
import java.util.List;

/**
 * Renders {@link Diagnostic}s in the rust-style format from spec §10.1:
 *
 * <pre>
 * error[FF1042]: currency mismatch in addition
 *   --&gt; management_fee.ff:7:23
 *    |
 *  7 |   let total = base_fee + EUR 1000
 *    |                          ^^^^^^^^
 *    = help: insert an FX conversion
 * </pre>
 */
public final class DiagnosticRenderer {

    private DiagnosticRenderer() {}

    public static String render(Diagnostic d, String source) {
        StringBuilder sb = new StringBuilder();
        sb.append(severityTag(d)).append('[').append(d.code().code()).append("]: ").append(d.message()).append('\n');
        sb.append("  --> ").append(d.location()).append('\n');
        appendSourceContext(sb, d.location(), source);
        d.hint().ifPresent(h -> sb.append("  = help: ").append(h).append('\n'));
        return sb.toString();
    }

    public static String renderAll(List<Diagnostic> diagnostics, String source) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(render(d, source));
        }
        return sb.toString();
    }

    private static String severityTag(Diagnostic d) {
        return d.severity().name().toLowerCase();
    }

    private static void appendSourceContext(StringBuilder sb, SourceLocation location, String source) {
        if (source == null || location.line() <= 0 || location == SourceLocation.UNKNOWN) {
            return;
        }
        String line = lineAt(source, location.line());
        if (line == null) return;
        String gutterPad = " ".repeat(String.valueOf(location.line()).length());
        sb.append(' ').append(gutterPad).append(" |\n");
        sb.append(' ').append(location.line()).append(" | ").append(line).append('\n');
        sb.append(' ').append(gutterPad).append(" | ");
        int col = Math.max(location.column() - 1, 0);
        sb.append(" ".repeat(col));
        sb.append("^".repeat(Math.max(location.length(), 1)));
        sb.append('\n');
    }

    private static String lineAt(String source, int oneBasedLine) {
        int idx = 1;
        int start = 0;
        while (idx < oneBasedLine) {
            int newline = source.indexOf('\n', start);
            if (newline < 0) return null;
            start = newline + 1;
            idx++;
        }
        int end = source.indexOf('\n', start);
        if (end < 0) end = source.length();
        // strip trailing \r if any
        if (end > start && source.charAt(end - 1) == '\r') end--;
        return source.substring(start, end);
    }
}
