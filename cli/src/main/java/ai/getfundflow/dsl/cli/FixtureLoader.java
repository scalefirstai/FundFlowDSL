package ai.getfundflow.dsl.cli;

import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import ai.getfundflow.dsl.runtime.RuntimeValue;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a minimal fixture file for {@code fundflow run --fixture}.
 *
 * <p>Format: one {@code key=value} per line. Keys are phrasal names (the DSL's
 * canonical form), values follow these shapes:
 *
 * <pre>
 * opening nav of share class = USD 10000000
 * call date                  = 2026-06-30
 * mgmt fee rate              = 1.5%
 * units outstanding          = 950000
 * investor weights           = [100000, 250000, 150000]
 * </pre>
 *
 * <p>This is intentionally small. The per-fund domain catalogue (WP-12) supersedes
 * it with a richer typed catalogue.
 */
public final class FixtureLoader {

    private static final Pattern MONEY = Pattern.compile("([A-Z]{3})\\s+(-?[\\d,_]+(?:\\.\\d+)?)");
    private static final Pattern PERCENT = Pattern.compile("(-?\\d+(?:\\.\\d+)?)%");
    private static final Pattern BPS = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*bps", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private FixtureLoader() {}

    public static Map<String, RuntimeValue> load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        Map<String, RuntimeValue> out = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException(
                        file + ":" + (i + 1) + " missing '=' in fixture line: " + raw);
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            out.put(key, parseValue(value));
        }
        return out;
    }

    static RuntimeValue parseValue(String value) {
        if (value.startsWith("[") && value.endsWith("]")) {
            return parseList(value.substring(1, value.length() - 1));
        }
        Matcher m;
        if ((m = MONEY.matcher(value)).matches()) {
            BigDecimal amount = new BigDecimal(m.group(2).replace(",", "").replace("_", ""));
            return new RuntimeValue.MoneyVal(Money.of(amount, Currency.getInstance(m.group(1))));
        }
        if ((m = PERCENT.matcher(value)).matches()) {
            return new RuntimeValue.PercentVal(Percentage.ofPercent(new BigDecimal(m.group(1))));
        }
        if ((m = BPS.matcher(value)).matches()) {
            return new RuntimeValue.PercentVal(Percentage.ofBps(new BigDecimal(m.group(1))));
        }
        if (DATE.matcher(value).matches()) {
            return new RuntimeValue.DateVal(LocalDate.parse(value));
        }
        if (NUMBER.matcher(value).matches()) {
            return new RuntimeValue.NumberVal(new BigDecimal(value));
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return new RuntimeValue.StringVal(value.substring(1, value.length() - 1));
        }
        return new RuntimeValue.StringVal(value);
    }

    private static RuntimeValue parseList(String body) {
        List<RuntimeValue> items = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            if (c == ',' && depth == 0) {
                items.add(parseValue(current.toString().trim()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().trim().isEmpty()) {
            items.add(parseValue(current.toString().trim()));
        }
        return new RuntimeValue.ListVal(items);
    }
}
