package ai.getfundflow.dsl.cli;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Hand-rolled, deterministic JSON writer for CLI outputs. Sorted-key Maps to keep
 * output stable across runs. Supports: Map, List, String, Number, Boolean, null.
 */
final class JsonWriter {

    private JsonWriter() {}

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        append(sb, value, 0, true);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object value, int depth, boolean pretty) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            appendString(sb, s);
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof BigDecimal bd) {
            sb.append(bd.toPlainString());
        } else if (value instanceof Number n) {
            sb.append(n);
        } else if (value instanceof Map<?, ?> m) {
            appendMap(sb, m, depth, pretty);
        } else if (value instanceof List<?> l) {
            appendList(sb, l, depth, pretty);
        } else {
            appendString(sb, value.toString());
        }
    }

    private static void appendMap(StringBuilder sb, Map<?, ?> map, int depth, boolean pretty) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : sortedEntries(map)) {
            if (!first) sb.append(',');
            first = false;
            if (pretty) newline(sb, depth + 1);
            appendString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            if (pretty) sb.append(' ');
            append(sb, e.getValue(), depth + 1, pretty);
        }
        if (pretty) newline(sb, depth);
        sb.append('}');
    }

    private static void appendList(StringBuilder sb, List<?> list, int depth, boolean pretty) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        boolean first = true;
        for (Object v : list) {
            if (!first) sb.append(',');
            first = false;
            if (pretty) newline(sb, depth + 1);
            append(sb, v, depth + 1, pretty);
        }
        if (pretty) newline(sb, depth);
        sb.append(']');
    }

    private static Iterable<? extends Map.Entry<?, ?>> sortedEntries(Map<?, ?> map) {
        return map.entrySet().stream()
                .sorted((a, b) -> String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey())))
                .toList();
    }

    private static void newline(StringBuilder sb, int depth) {
        sb.append('\n');
        for (int i = 0; i < depth; i++) sb.append("  ");
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
