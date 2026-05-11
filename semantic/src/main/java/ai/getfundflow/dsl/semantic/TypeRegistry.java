package ai.getfundflow.dsl.semantic;

import ai.getfundflow.dsl.semantic.DslType.BigDecimalType;
import ai.getfundflow.dsl.semantic.DslType.BooleanType;
import ai.getfundflow.dsl.semantic.DslType.BusinessDateType;
import ai.getfundflow.dsl.semantic.DslType.DayCountType;
import ai.getfundflow.dsl.semantic.DslType.MoneyType;
import ai.getfundflow.dsl.semantic.DslType.PercentageType;
import ai.getfundflow.dsl.semantic.DslType.PeriodType;
import ai.getfundflow.dsl.semantic.DslType.QuantityType;
import ai.getfundflow.dsl.semantic.DslType.StringType;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Built-in DSL base types and user-declared extensions (WP-11).
 *
 * <p>Base types correspond to the records in {@code core/types/}: Fund, ShareClass,
 * Series, Investor, Position, Transaction, Cashflow, NAV, LedgerAccount. Each can
 * be extended by zero or more {@code type extension X extends &lt;base&gt; { … }}
 * declarations; fields aggregate across extensions of the same base.
 *
 * <p>Built-in field type names (used in {@code field foo: T}):
 * Money, Percentage, Number, Boolean, BusinessDate, Period, DayCount, Quantity, String.
 */
public final class TypeRegistry {

    private static final Set<String> BASE_TYPES = Set.of(
            "Fund", "ShareClass", "Series", "Investor", "Position",
            "Transaction", "Cashflow", "NAV", "LedgerAccount");

    /** Field-type identifier → DSL type. Lookup is case-sensitive. */
    private static final Map<String, DslType> FIELD_TYPES = Map.ofEntries(
            Map.entry("Money", MoneyType.anyCurrency()),
            Map.entry("Percentage", PercentageType.INSTANCE),
            Map.entry("Number", BigDecimalType.INSTANCE),
            Map.entry("BigDecimal", BigDecimalType.INSTANCE),
            Map.entry("Boolean", BooleanType.INSTANCE),
            Map.entry("BusinessDate", BusinessDateType.INSTANCE),
            Map.entry("Date", BusinessDateType.INSTANCE),
            Map.entry("Period", PeriodType.INSTANCE),
            Map.entry("DayCount", DayCountType.INSTANCE),
            Map.entry("Quantity", QuantityType.INSTANCE),
            Map.entry("String", StringType.INSTANCE));

    /** All declared extensions, indexed by extension name. */
    private final Map<String, ExtensionInfo> extensions = new LinkedHashMap<>();

    /** Fields aggregated across every extension targeting a given base type. */
    private final Map<String, Map<String, DslType>> fieldsByBase = new LinkedHashMap<>();

    public boolean isBaseType(String name) {
        return BASE_TYPES.contains(name) || BASE_TYPES.contains(capitalize(name));
    }

    public Optional<String> canonicalBase(String name) {
        if (BASE_TYPES.contains(name)) return Optional.of(name);
        String capped = capitalize(name);
        if (BASE_TYPES.contains(capped)) return Optional.of(capped);
        return Optional.empty();
    }

    public Optional<DslType> resolveFieldType(String typeRefName) {
        return Optional.ofNullable(FIELD_TYPES.get(typeRefName));
    }

    public boolean registerExtension(ExtensionInfo info) {
        if (extensions.containsKey(info.name())) return false;
        extensions.put(info.name(), info);
        Map<String, DslType> bucket = fieldsByBase.computeIfAbsent(info.baseType(), k -> new LinkedHashMap<>());
        bucket.putAll(info.fields());
        return true;
    }

    public Optional<DslType> resolveExtensionField(String baseTypeName, String fieldName) {
        String base = canonicalBase(baseTypeName).orElse(null);
        if (base == null) return Optional.empty();
        Map<String, DslType> fields = fieldsByBase.get(base);
        if (fields == null) return Optional.empty();
        return Optional.ofNullable(fields.get(fieldName));
    }

    public Map<String, ExtensionInfo> extensions() {
        return Map.copyOf(extensions);
    }

    public static Set<String> baseTypes() {
        return BASE_TYPES;
    }

    public static Set<String> fieldTypeNames() {
        return FIELD_TYPES.keySet();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    public record ExtensionInfo(String name, String baseType, Map<String, DslType> fields) {
        public ExtensionInfo {
            fields = Map.copyOf(fields);
        }
    }
}
