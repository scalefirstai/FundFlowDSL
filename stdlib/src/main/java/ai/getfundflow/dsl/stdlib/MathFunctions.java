package ai.getfundflow.dsl.stdlib;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;

public final class MathFunctions {

    private static final MathContext MC = MathContext.DECIMAL64;

    private MathFunctions() {}

    public static BigDecimal abs(BigDecimal x) {
        return x.abs();
    }

    public static BigDecimal round(BigDecimal x, int digits) {
        return x.setScale(digits, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal ceiling(BigDecimal x) {
        return x.setScale(0, RoundingMode.CEILING);
    }

    public static BigDecimal floor(BigDecimal x) {
        return x.setScale(0, RoundingMode.FLOOR);
    }

    public static BigDecimal truncate(BigDecimal x) {
        return x.setScale(0, RoundingMode.DOWN);
    }

    public static BigDecimal mod(BigDecimal x, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            throw new ArithmeticException("mod: divisor is zero");
        }
        return x.remainder(divisor, MC);
    }

    public static BigDecimal power(BigDecimal base, BigDecimal exponent) {
        if (exponent.scale() <= 0 && exponent.abs().compareTo(BigDecimal.valueOf(999)) <= 0) {
            return base.pow(exponent.intValueExact(), MC);
        }
        double result = Math.pow(base.doubleValue(), exponent.doubleValue());
        return new BigDecimal(result, MC);
    }

    public static BigDecimal sqrt(BigDecimal x) {
        if (x.signum() < 0) {
            throw new ArithmeticException("sqrt: negative argument");
        }
        return x.sqrt(MC);
    }

    public static int sign(BigDecimal x) {
        return x.signum();
    }

    public static BigDecimal max(BigDecimal first, BigDecimal... rest) {
        BigDecimal best = first;
        for (BigDecimal v : rest) {
            if (v.compareTo(best) > 0) {
                best = v;
            }
        }
        return best;
    }

    public static BigDecimal min(BigDecimal first, BigDecimal... rest) {
        BigDecimal best = first;
        for (BigDecimal v : rest) {
            if (v.compareTo(best) < 0) {
                best = v;
            }
        }
        return best;
    }

    public static BigDecimal max(BigDecimal[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("max: empty input");
        }
        return max(values[0], Arrays.copyOfRange(values, 1, values.length));
    }

    public static BigDecimal min(BigDecimal[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("min: empty input");
        }
        return min(values[0], Arrays.copyOfRange(values, 1, values.length));
    }
}
