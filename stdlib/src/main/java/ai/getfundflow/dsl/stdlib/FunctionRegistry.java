package ai.getfundflow.dsl.stdlib;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class FunctionRegistry {

    public enum Category { MATH, STATS, FINANCIAL, DATE }

    public record Signature(String name, Category category, int minArity, int maxArity, String summary) {}

    private static final Map<String, Signature> REGISTRY = new TreeMap<>();

    static {
        // Math
        register("abs", Category.MATH, 1, 1, "Absolute value of x");
        register("round", Category.MATH, 2, 2, "Banker's-rounded x to N digits");
        register("ceiling", Category.MATH, 1, 1, "Smallest integer >= x");
        register("floor", Category.MATH, 1, 1, "Largest integer <= x");
        register("truncate", Category.MATH, 1, 1, "Integer part of x (towards zero)");
        register("mod", Category.MATH, 2, 2, "x mod divisor");
        register("power", Category.MATH, 2, 2, "x raised to the power of y");
        register("sqrt", Category.MATH, 1, 1, "Square root of x");
        register("sign", Category.MATH, 1, 1, "-1, 0, or 1 indicating sign of x");
        register("max", Category.MATH, 1, Integer.MAX_VALUE, "Maximum of variadic arguments");
        register("min", Category.MATH, 1, Integer.MAX_VALUE, "Minimum of variadic arguments");

        // Stats
        register("count", Category.STATS, 1, 1, "Cardinality of a list");
        register("sum", Category.STATS, 1, 1, "Sum of a list (function form; see also `sum of`)");
        register("average", Category.STATS, 1, 1, "Arithmetic mean");
        register("median", Category.STATS, 1, 1, "Median value");
        register("variance", Category.STATS, 1, 1, "Sample variance");
        register("stdev", Category.STATS, 1, 1, "Sample standard deviation");
        register("variance_population", Category.STATS, 1, 1, "Population variance");
        register("stdev_population", Category.STATS, 1, 1, "Population standard deviation");

        // Financial
        register("npv", Category.FINANCIAL, 2, 2, "Net present value (Excel: cashflows at end of each period)");
        register("irr", Category.FINANCIAL, 1, 2, "Internal rate of return; optional initial guess");
        register("xnpv", Category.FINANCIAL, 3, 3, "Date-aware NPV with arbitrary timing");
        register("xirr", Category.FINANCIAL, 2, 3, "Date-aware IRR with arbitrary timing");
        register("pv", Category.FINANCIAL, 3, 4, "Present value of an annuity");
        register("fv", Category.FINANCIAL, 3, 4, "Future value of an annuity");
        register("pmt", Category.FINANCIAL, 3, 4, "Periodic payment for an annuity");

        // Date
        register("year", Category.DATE, 1, 1, "Year component of a date");
        register("month", Category.DATE, 1, 1, "Month component (1..12) of a date");
        register("day", Category.DATE, 1, 1, "Day-of-month component of a date");
        register("edate", Category.DATE, 2, 2, "Date offset by N months (clamped to month-end)");
        register("eomonth", Category.DATE, 2, 2, "End of month, N months from given date");
        register("datediff", Category.DATE, 3, 3, "Difference between two dates in given unit");
    }

    private FunctionRegistry() {}

    private static void register(String name, Category category, int min, int max, String summary) {
        REGISTRY.put(name, new Signature(name, category, min, max, summary));
    }

    public static Optional<Signature> lookup(String name) {
        return Optional.ofNullable(REGISTRY.get(name));
    }

    public static boolean isKnown(String name) {
        return REGISTRY.containsKey(name);
    }

    public static Set<String> names() {
        return REGISTRY.keySet();
    }

    public static Map<String, Signature> all() {
        return Map.copyOf(REGISTRY);
    }
}
