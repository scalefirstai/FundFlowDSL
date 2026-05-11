package ai.getfundflow.dsl.diagnostics;

import ai.getfundflow.dsl.ast.PrettyPrinter;
import ai.getfundflow.dsl.ast.Program;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source-code formatter implementing spec §10.2:
 *
 * <ul>
 *   <li>Two-space indentation inside {@code rule { }} blocks (delegated to {@link PrettyPrinter})</li>
 *   <li>One blank line between top-level declarations (delegated)</li>
 *   <li>Keywords lowercase, identifiers as written, currency codes uppercase (delegated)</li>
 *   <li>Money literals: thousands separators canonicalized to commas (this layer)</li>
 *   <li>Trailing whitespace stripped, files end with newline (this layer)</li>
 * </ul>
 *
 * <p>Idempotent: {@code format(format(program)) == format(program)} for all valid programs
 * — see {@code FormatterIdempotenceTest}.
 */
public final class Formatter {

    /**
     * Matches a money literal in the printer's flat output: 3-letter currency code,
     * one or more spaces, then digits and optional decimal portion.
     */
    private static final Pattern MONEY = Pattern.compile(
            "(\\b[A-Z]{3})\\s+(-?\\d+)(\\.\\d+)?\\b");

    private Formatter() {}

    public static String format(Program program) {
        String raw = PrettyPrinter.print(program);
        String withCommas = canonicalizeMoney(raw);
        String trimmed = stripTrailingWhitespace(withCommas);
        return ensureTrailingNewline(trimmed);
    }

    private static String canonicalizeMoney(String text) {
        Matcher m = MONEY.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (m.find()) {
            String currency = m.group(1);
            String integerPart = m.group(2);
            String decimalPart = m.group(3) == null ? "" : m.group(3);
            m.appendReplacement(out, Matcher.quoteReplacement(
                    currency + " " + addThousandsSeparators(integerPart) + decimalPart));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String addThousandsSeparators(String integerPart) {
        boolean negative = integerPart.startsWith("-");
        String digits = negative ? integerPart.substring(1) : integerPart;
        if (digits.length() <= 3) return integerPart;
        StringBuilder sb = new StringBuilder();
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) sb.append(',');
            sb.append(digits.charAt(i));
        }
        return (negative ? "-" : "") + sb;
    }

    private static String stripTrailingWhitespace(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int lineStart = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                appendStripped(sb, text, lineStart, i);
                sb.append('\n');
                lineStart = i + 1;
            }
        }
        if (lineStart < text.length()) {
            appendStripped(sb, text, lineStart, text.length());
        }
        return sb.toString();
    }

    private static void appendStripped(StringBuilder sb, String text, int start, int end) {
        int last = end;
        while (last > start) {
            char c = text.charAt(last - 1);
            if (c == ' ' || c == '\t' || c == '\r') last--;
            else break;
        }
        sb.append(text, start, last);
    }

    private static String ensureTrailingNewline(String text) {
        if (text.isEmpty() || text.charAt(text.length() - 1) != '\n') {
            return text + "\n";
        }
        return text;
    }
}
