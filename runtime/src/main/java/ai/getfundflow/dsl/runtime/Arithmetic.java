package ai.getfundflow.dsl.runtime;

import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import ai.getfundflow.dsl.runtime.RuntimeValue.BoolVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.MoneyVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NullVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NumberVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.PercentVal;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.function.IntPredicate;

final class Arithmetic {

    private static final MathContext MC = MathContext.DECIMAL64;

    private Arithmetic() {}

    static RuntimeValue add(RuntimeValue left, RuntimeValue right) {
        if (left instanceof MoneyVal lm && right instanceof MoneyVal rm) {
            return new MoneyVal(lm.value().plus(rm.value()));
        }
        if (left instanceof PercentVal lp && right instanceof PercentVal rp) {
            return new PercentVal(lp.value().plus(rp.value()));
        }
        if (left instanceof NumberVal ln && right instanceof NumberVal rn) {
            return new NumberVal(ln.value().add(rn.value()));
        }
        throw new EvaluationException("cannot add " + left + " + " + right);
    }

    static RuntimeValue subtract(RuntimeValue left, RuntimeValue right) {
        if (left instanceof MoneyVal lm && right instanceof MoneyVal rm) {
            return new MoneyVal(lm.value().minus(rm.value()));
        }
        if (left instanceof PercentVal lp && right instanceof PercentVal rp) {
            return new PercentVal(lp.value().minus(rp.value()));
        }
        if (left instanceof NumberVal ln && right instanceof NumberVal rn) {
            return new NumberVal(ln.value().subtract(rn.value()));
        }
        throw new EvaluationException("cannot subtract " + left + " - " + right);
    }

    static RuntimeValue multiply(RuntimeValue left, RuntimeValue right) {
        if (left instanceof MoneyVal m && right instanceof PercentVal p) {
            return new MoneyVal(m.value().multiply(p.value()));
        }
        if (left instanceof PercentVal p && right instanceof MoneyVal m) {
            return new MoneyVal(m.value().multiply(p.value()));
        }
        if (left instanceof MoneyVal m && right instanceof NumberVal n) {
            return new MoneyVal(m.value().multiply(n.value()));
        }
        if (left instanceof NumberVal n && right instanceof MoneyVal m) {
            return new MoneyVal(m.value().multiply(n.value()));
        }
        if (left instanceof PercentVal lp && right instanceof PercentVal rp) {
            return new PercentVal(new Percentage(lp.value().asRatio().multiply(rp.value().asRatio(), MC)));
        }
        if (left instanceof NumberVal ln && right instanceof NumberVal rn) {
            return new NumberVal(ln.value().multiply(rn.value(), MC));
        }
        if (left instanceof PercentVal lp && right instanceof NumberVal rn) {
            return new PercentVal(new Percentage(lp.value().asRatio().multiply(rn.value(), MC)));
        }
        if (left instanceof NumberVal ln && right instanceof PercentVal rp) {
            return new PercentVal(new Percentage(rp.value().asRatio().multiply(ln.value(), MC)));
        }
        throw new EvaluationException("cannot multiply " + left + " * " + right);
    }

    static RuntimeValue divide(RuntimeValue left, RuntimeValue right) {
        if (left instanceof MoneyVal lm && right instanceof MoneyVal rm) {
            BigDecimal ratio = lm.value().amount().divide(rm.value().amount(), MC);
            return new NumberVal(ratio);
        }
        if (left instanceof MoneyVal m && right instanceof NumberVal n) {
            BigDecimal scaled = m.value().amount().divide(n.value(), MC);
            return new MoneyVal(Money.of(scaled, m.value().currency()));
        }
        if (left instanceof NumberVal ln && right instanceof NumberVal rn) {
            return new NumberVal(ln.value().divide(rn.value(), MC));
        }
        if (left instanceof PercentVal lp && right instanceof NumberVal rn) {
            return new PercentVal(new Percentage(lp.value().asRatio().divide(rn.value(), MC)));
        }
        throw new EvaluationException("cannot divide " + left + " / " + right);
    }

    static RuntimeValue compare(RuntimeValue left, RuntimeValue right, IntPredicate predicate) {
        int cmp = compareTo(left, right);
        return new BoolVal(predicate.test(cmp));
    }

    private static int compareTo(RuntimeValue left, RuntimeValue right) {
        if (left instanceof MoneyVal lm && right instanceof MoneyVal rm) {
            requireSameCurrency(lm, rm);
            return lm.value().amount().compareTo(rm.value().amount());
        }
        if (left instanceof BoolVal lb && right instanceof BoolVal rb) {
            return Boolean.compare(lb.value(), rb.value());
        }
        // Numeric / cross-type compare: any numeric-like RuntimeValue (Money, Percentage,
        // Number) participates in BigDecimal-based ordering. This supports the common
        // `max(0, money)` floor-at-zero idiom where 0 is Number but the other side is Money.
        BigDecimal lb2 = numericOrNull(left);
        BigDecimal rb2 = numericOrNull(right);
        if (lb2 != null && rb2 != null) return lb2.compareTo(rb2);
        throw new EvaluationException("cannot compare " + left + " with " + right);
    }

    private static BigDecimal numericOrNull(RuntimeValue v) {
        return switch (v) {
            case NumberVal n -> n.value();
            case PercentVal p -> p.value().asRatio();
            case MoneyVal m -> m.value().amount();
            default -> null;
        };
    }

    private static void requireSameCurrency(MoneyVal a, MoneyVal b) {
        if (!a.value().currency().equals(b.value().currency())) {
            throw new EvaluationException(
                    "currency mismatch: " + a.value().currency().getCurrencyCode()
                            + " vs " + b.value().currency().getCurrencyCode());
        }
    }

    static RuntimeValue sumList(List<RuntimeValue> values) {
        if (values.isEmpty()) return new NumberVal(BigDecimal.ZERO);
        RuntimeValue acc = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            acc = add(acc, values.get(i));
        }
        return acc;
    }

    static int compareForSort(RuntimeValue left, RuntimeValue right) {
        if (left instanceof NullVal && right instanceof NullVal) return 0;
        if (left instanceof NullVal) return -1;
        if (right instanceof NullVal) return 1;
        return compareTo(left, right);
    }
}
