package ai.getfundflow.dsl.stdlib;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class FinancialFunctions {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final int MAX_ITERS = 200;
    private static final double CONVERGENCE = 1e-10;
    private static final BigDecimal DEFAULT_GUESS = new BigDecimal("0.1");

    private FinancialFunctions() {}

    public static BigDecimal npv(BigDecimal rate, List<BigDecimal> cashflows) {
        BigDecimal onePlusR = ONE.add(rate);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal denom = onePlusR;
        for (BigDecimal cf : cashflows) {
            total = total.add(cf.divide(denom, MC));
            denom = denom.multiply(onePlusR, MC);
        }
        return total;
    }

    public static BigDecimal irr(List<BigDecimal> cashflows) {
        return irr(cashflows, DEFAULT_GUESS);
    }

    public static BigDecimal irr(List<BigDecimal> cashflows, BigDecimal guess) {
        if (cashflows.size() < 2) {
            throw new IllegalArgumentException("irr: need at least 2 cashflows");
        }
        double r = guess.doubleValue();
        for (int iter = 0; iter < MAX_ITERS; iter++) {
            double npv = 0.0;
            double dnpv = 0.0;
            for (int i = 0; i < cashflows.size(); i++) {
                double cf = cashflows.get(i).doubleValue();
                double pow = Math.pow(1.0 + r, i);
                npv += cf / pow;
                if (i > 0) {
                    dnpv -= i * cf / (pow * (1.0 + r));
                }
            }
            if (Math.abs(npv) < CONVERGENCE) {
                return new BigDecimal(r, MC);
            }
            if (dnpv == 0.0) {
                throw new ArithmeticException("irr: derivative is zero, cannot converge");
            }
            double next = r - npv / dnpv;
            if (Math.abs(next - r) < CONVERGENCE) {
                return new BigDecimal(next, MC);
            }
            r = next;
        }
        throw new ArithmeticException("irr: did not converge after " + MAX_ITERS + " iterations");
    }

    public static BigDecimal xnpv(BigDecimal rate, List<BigDecimal> cashflows, List<LocalDate> dates) {
        if (cashflows.size() != dates.size()) {
            throw new IllegalArgumentException("xnpv: cashflows and dates length mismatch");
        }
        if (cashflows.isEmpty()) {
            throw new IllegalArgumentException("xnpv: empty input");
        }
        LocalDate d0 = dates.get(0);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < cashflows.size(); i++) {
            BigDecimal yearFraction = BigDecimal
                    .valueOf(ChronoUnit.DAYS.between(d0, dates.get(i)))
                    .divide(DAYS_PER_YEAR, MC);
            double divisor = Math.pow(1.0 + rate.doubleValue(), yearFraction.doubleValue());
            total = total.add(cashflows.get(i).divide(new BigDecimal(divisor, MC), MC));
        }
        return total;
    }

    public static BigDecimal xirr(List<BigDecimal> cashflows, List<LocalDate> dates) {
        return xirr(cashflows, dates, DEFAULT_GUESS);
    }

    public static BigDecimal xirr(List<BigDecimal> cashflows, List<LocalDate> dates, BigDecimal guess) {
        if (cashflows.size() != dates.size()) {
            throw new IllegalArgumentException("xirr: cashflows and dates length mismatch");
        }
        if (cashflows.size() < 2) {
            throw new IllegalArgumentException("xirr: need at least 2 cashflows");
        }
        LocalDate d0 = dates.get(0);
        double[] cf = new double[cashflows.size()];
        double[] yf = new double[cashflows.size()];
        for (int i = 0; i < cashflows.size(); i++) {
            cf[i] = cashflows.get(i).doubleValue();
            yf[i] = ChronoUnit.DAYS.between(d0, dates.get(i)) / 365.0;
        }
        double r = guess.doubleValue();
        for (int iter = 0; iter < MAX_ITERS; iter++) {
            double f = 0.0;
            double df = 0.0;
            for (int i = 0; i < cf.length; i++) {
                double pow = Math.pow(1.0 + r, yf[i]);
                f += cf[i] / pow;
                if (yf[i] != 0.0) {
                    df -= yf[i] * cf[i] / (pow * (1.0 + r));
                }
            }
            if (Math.abs(f) < CONVERGENCE) {
                return new BigDecimal(r, MC);
            }
            if (df == 0.0) {
                throw new ArithmeticException("xirr: derivative is zero");
            }
            double next = r - f / df;
            if (Math.abs(next - r) < CONVERGENCE) {
                return new BigDecimal(next, MC);
            }
            r = next;
        }
        throw new ArithmeticException("xirr: did not converge after " + MAX_ITERS + " iterations");
    }

    public static BigDecimal pv(BigDecimal rate, int nper, BigDecimal pmt) {
        return pv(rate, nper, pmt, BigDecimal.ZERO);
    }

    public static BigDecimal pv(BigDecimal rate, int nper, BigDecimal pmt, BigDecimal fv) {
        if (rate.signum() == 0) {
            return pmt.multiply(BigDecimal.valueOf(nper)).add(fv).negate();
        }
        BigDecimal onePlusR = ONE.add(rate);
        BigDecimal pow = onePlusR.pow(nper, MC);
        BigDecimal annuityFactor = ONE.subtract(ONE.divide(pow, MC)).divide(rate, MC);
        BigDecimal pmtPart = pmt.multiply(annuityFactor, MC);
        BigDecimal fvPart = fv.divide(pow, MC);
        return pmtPart.add(fvPart).negate();
    }

    public static BigDecimal fv(BigDecimal rate, int nper, BigDecimal pmt) {
        return fv(rate, nper, pmt, BigDecimal.ZERO);
    }

    public static BigDecimal fv(BigDecimal rate, int nper, BigDecimal pmt, BigDecimal pv) {
        if (rate.signum() == 0) {
            return pmt.multiply(BigDecimal.valueOf(nper)).add(pv).negate();
        }
        BigDecimal onePlusR = ONE.add(rate);
        BigDecimal pow = onePlusR.pow(nper, MC);
        BigDecimal annuityFactor = pow.subtract(ONE).divide(rate, MC);
        BigDecimal pmtPart = pmt.multiply(annuityFactor, MC);
        BigDecimal pvPart = pv.multiply(pow, MC);
        return pmtPart.add(pvPart).negate();
    }

    public static BigDecimal pmt(BigDecimal rate, int nper, BigDecimal pv) {
        return pmt(rate, nper, pv, BigDecimal.ZERO);
    }

    public static BigDecimal pmt(BigDecimal rate, int nper, BigDecimal pv, BigDecimal fv) {
        if (nper <= 0) {
            throw new IllegalArgumentException("pmt: nper must be positive");
        }
        if (rate.signum() == 0) {
            return pv.add(fv).divide(BigDecimal.valueOf(nper), MC).negate();
        }
        BigDecimal onePlusR = ONE.add(rate);
        BigDecimal pow = onePlusR.pow(nper, MC);
        BigDecimal numerator = pv.multiply(pow, MC).add(fv);
        BigDecimal denominator = pow.subtract(ONE).divide(rate, MC);
        return numerator.divide(denominator, MC).negate();
    }
}
