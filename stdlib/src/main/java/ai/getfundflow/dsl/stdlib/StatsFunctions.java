package ai.getfundflow.dsl.stdlib;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StatsFunctions {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWO = new BigDecimal("2");

    private StatsFunctions() {}

    public static int count(List<?> values) {
        return values.size();
    }

    public static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            total = total.add(v);
        }
        return total;
    }

    public static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("average: empty input");
        }
        return sum(values).divide(BigDecimal.valueOf(values.size()), MC);
    }

    public static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("median: empty input");
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(TWO, MC);
    }

    public static BigDecimal variance(List<BigDecimal> values) {
        return varianceInternal(values, true);
    }

    public static BigDecimal variancePopulation(List<BigDecimal> values) {
        return varianceInternal(values, false);
    }

    public static BigDecimal stdev(List<BigDecimal> values) {
        return variance(values).sqrt(MC);
    }

    public static BigDecimal stdevPopulation(List<BigDecimal> values) {
        return variancePopulation(values).sqrt(MC);
    }

    private static BigDecimal varianceInternal(List<BigDecimal> values, boolean sample) {
        int n = values.size();
        if (sample && n < 2) {
            throw new IllegalArgumentException("sample variance: need at least 2 values");
        }
        if (!sample && n < 1) {
            throw new IllegalArgumentException("population variance: empty input");
        }
        BigDecimal mean = average(values);
        BigDecimal sumSq = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            BigDecimal d = v.subtract(mean);
            sumSq = sumSq.add(d.multiply(d, MC));
        }
        BigDecimal denom = BigDecimal.valueOf(sample ? n - 1 : n);
        return sumSq.divide(denom, MC);
    }
}
