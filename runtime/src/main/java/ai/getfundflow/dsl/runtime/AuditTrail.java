package ai.getfundflow.dsl.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

public final class AuditTrail implements AuditSink {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void record(AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    /** Stable hash of the trail contents — used by determinism tests. */
    public String contentHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (AuditEntry e : entries) {
                md.update(canonical(e).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String canonical(AuditEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.rule()).append('|').append(e.description()).append('|');
        e.inputs().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(en -> sb.append(en.getKey()).append('=')
                        .append(formatValue(en.getValue())).append(';'));
        sb.append("=>").append(formatValue(e.output())).append('\n');
        return sb.toString();
    }

    private static String formatValue(RuntimeValue v) {
        return switch (v) {
            case RuntimeValue.MoneyVal m ->
                    "Money(" + m.value().currency().getCurrencyCode()
                            + "," + m.value().amount().toPlainString() + ")";
            case RuntimeValue.PercentVal p ->
                    "Pct(" + p.value().asRatio().toPlainString() + ")";
            case RuntimeValue.NumberVal n -> "Num(" + n.value().toPlainString() + ")";
            case RuntimeValue.BoolVal b -> "Bool(" + b.value() + ")";
            case RuntimeValue.DateVal d -> "Date(" + d.value() + ")";
            case RuntimeValue.PeriodVal p -> "Period(" + p.start() + "," + p.end() + ")";
            case RuntimeValue.DayCountVal d -> "DC(" + d.value().code() + ")";
            case RuntimeValue.StringVal s -> "Str(" + s.value() + ")";
            case RuntimeValue.ListVal l -> "List(" + l.values().size() + ")";
            case RuntimeValue.NullVal n -> "null";
        };
    }
}
