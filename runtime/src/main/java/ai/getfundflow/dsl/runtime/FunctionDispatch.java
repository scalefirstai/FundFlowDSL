package ai.getfundflow.dsl.runtime;

import ai.getfundflow.dsl.runtime.RuntimeValue.DateVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.ListVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.MoneyVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NullVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NumberVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.PercentVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.StringVal;
import ai.getfundflow.dsl.stdlib.DateFunctions;
import ai.getfundflow.dsl.stdlib.FinancialFunctions;
import ai.getfundflow.dsl.stdlib.MathFunctions;
import ai.getfundflow.dsl.stdlib.StatsFunctions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class FunctionDispatch {

    private FunctionDispatch() {}

    static RuntimeValue invoke(String name, List<RuntimeValue> args) {
        if (args.stream().anyMatch(a -> a instanceof NullVal)) {
            return NullVal.INSTANCE;
        }
        return switch (name) {
            // Math
            case "abs" -> num(MathFunctions.abs(asNumber(args.get(0))));
            case "round" -> num(MathFunctions.round(asNumber(args.get(0)), asInt(args.get(1))));
            case "ceiling" -> num(MathFunctions.ceiling(asNumber(args.get(0))));
            case "floor" -> num(MathFunctions.floor(asNumber(args.get(0))));
            case "truncate" -> num(MathFunctions.truncate(asNumber(args.get(0))));
            case "mod" -> num(MathFunctions.mod(asNumber(args.get(0)), asNumber(args.get(1))));
            case "power" -> num(MathFunctions.power(asNumber(args.get(0)), asNumber(args.get(1))));
            case "sqrt" -> num(MathFunctions.sqrt(asNumber(args.get(0))));
            case "sign" -> num(BigDecimal.valueOf(MathFunctions.sign(asNumber(args.get(0)))));
            case "max" -> compareFold(args, +1);
            case "min" -> compareFold(args, -1);

            // Stats
            case "count" -> num(BigDecimal.valueOf(StatsFunctions.count(toList(args.get(0)))));
            case "sum" -> num(StatsFunctions.sum(toBigDecimalList(args.get(0))));
            case "average" -> num(StatsFunctions.average(toBigDecimalList(args.get(0))));
            case "median" -> num(StatsFunctions.median(toBigDecimalList(args.get(0))));
            case "variance" -> num(StatsFunctions.variance(toBigDecimalList(args.get(0))));
            case "stdev" -> num(StatsFunctions.stdev(toBigDecimalList(args.get(0))));
            case "variance_population" -> num(StatsFunctions.variancePopulation(toBigDecimalList(args.get(0))));
            case "stdev_population" -> num(StatsFunctions.stdevPopulation(toBigDecimalList(args.get(0))));

            // Financial
            case "npv" -> num(FinancialFunctions.npv(asNumber(args.get(0)), toBigDecimalList(args.get(1))));
            case "irr" -> num(args.size() == 1
                    ? FinancialFunctions.irr(toBigDecimalList(args.get(0)))
                    : FinancialFunctions.irr(toBigDecimalList(args.get(0)), asNumber(args.get(1))));
            case "xnpv" -> num(FinancialFunctions.xnpv(
                    asNumber(args.get(0)),
                    toBigDecimalList(args.get(1)),
                    toDateList(args.get(2))));
            case "xirr" -> num(args.size() == 2
                    ? FinancialFunctions.xirr(toBigDecimalList(args.get(0)), toDateList(args.get(1)))
                    : FinancialFunctions.xirr(
                            toBigDecimalList(args.get(0)), toDateList(args.get(1)), asNumber(args.get(2))));
            case "pv" -> num(args.size() == 3
                    ? FinancialFunctions.pv(asNumber(args.get(0)), asInt(args.get(1)), asNumber(args.get(2)))
                    : FinancialFunctions.pv(
                            asNumber(args.get(0)), asInt(args.get(1)),
                            asNumber(args.get(2)), asNumber(args.get(3))));
            case "fv" -> num(args.size() == 3
                    ? FinancialFunctions.fv(asNumber(args.get(0)), asInt(args.get(1)), asNumber(args.get(2)))
                    : FinancialFunctions.fv(
                            asNumber(args.get(0)), asInt(args.get(1)),
                            asNumber(args.get(2)), asNumber(args.get(3))));
            case "pmt" -> num(args.size() == 3
                    ? FinancialFunctions.pmt(asNumber(args.get(0)), asInt(args.get(1)), asNumber(args.get(2)))
                    : FinancialFunctions.pmt(
                            asNumber(args.get(0)), asInt(args.get(1)),
                            asNumber(args.get(2)), asNumber(args.get(3))));

            // Date
            case "year" -> num(BigDecimal.valueOf(DateFunctions.year(asDate(args.get(0)))));
            case "month" -> num(BigDecimal.valueOf(DateFunctions.month(asDate(args.get(0)))));
            case "day" -> num(BigDecimal.valueOf(DateFunctions.day(asDate(args.get(0)))));
            case "edate" -> new DateVal(DateFunctions.edate(asDate(args.get(0)), asInt(args.get(1))));
            case "eomonth" -> new DateVal(DateFunctions.eomonth(asDate(args.get(0)), asInt(args.get(1))));
            case "datediff" -> num(BigDecimal.valueOf(DateFunctions.datediff(
                    asDate(args.get(0)), asDate(args.get(1)), asString(args.get(2)))));

            default -> throw new EvaluationException("unknown function: " + name);
        };
    }

    private static RuntimeValue num(BigDecimal v) {
        return new NumberVal(v);
    }

    private static BigDecimal asNumber(RuntimeValue v) {
        return switch (v) {
            case NumberVal n -> n.value();
            case PercentVal p -> p.value().asRatio();
            case MoneyVal m -> m.value().amount();
            default -> throw new EvaluationException("expected a number, got " + v);
        };
    }

    private static int asInt(RuntimeValue v) {
        return asNumber(v).intValueExact();
    }

    private static LocalDate asDate(RuntimeValue v) {
        if (v instanceof DateVal d) return d.value();
        throw new EvaluationException("expected a Date, got " + v);
    }

    private static String asString(RuntimeValue v) {
        if (v instanceof StringVal s) return s.value();
        throw new EvaluationException("expected a String, got " + v);
    }

    private static List<RuntimeValue> toList(RuntimeValue v) {
        if (v instanceof ListVal lv) return lv.values();
        throw new EvaluationException("expected a list, got " + v);
    }

    private static List<BigDecimal> toBigDecimalList(RuntimeValue v) {
        List<BigDecimal> out = new ArrayList<>();
        for (RuntimeValue rv : toList(v)) out.add(asNumber(rv));
        return out;
    }

    private static List<LocalDate> toDateList(RuntimeValue v) {
        List<LocalDate> out = new ArrayList<>();
        for (RuntimeValue rv : toList(v)) out.add(asDate(rv));
        return out;
    }

    private static RuntimeValue compareFold(List<RuntimeValue> args, int direction) {
        RuntimeValue best = args.get(0);
        for (int i = 1; i < args.size(); i++) {
            int cmp = Arithmetic.compareForSort(best, args.get(i));
            if ((direction > 0 && cmp < 0) || (direction < 0 && cmp > 0)) {
                best = args.get(i);
            }
        }
        return promoteToCommonType(best, args);
    }

    /**
     * If the chosen value is a NumberVal but a sibling argument is Money or Percentage,
     * promote the bare number into that more-specific type. This lets `max(0, money)`
     * return Money(0, currency) instead of NumberVal(0).
     */
    private static RuntimeValue promoteToCommonType(RuntimeValue value, List<RuntimeValue> all) {
        if (!(value instanceof NumberVal n)) return value;
        for (RuntimeValue v : all) {
            if (v instanceof RuntimeValue.MoneyVal m) {
                return new RuntimeValue.MoneyVal(
                        ai.getfundflow.dsl.core.types.Money.of(n.value(), m.value().currency()));
            }
            if (v instanceof RuntimeValue.PercentVal) {
                return new RuntimeValue.PercentVal(
                        new ai.getfundflow.dsl.core.types.Percentage(n.value()));
            }
        }
        return value;
    }
}
