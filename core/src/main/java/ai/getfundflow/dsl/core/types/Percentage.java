package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public record Percentage(BigDecimal value) {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    public Percentage {
        Objects.requireNonNull(value, "value");
    }

    public static Percentage ofPercent(BigDecimal pct) {
        return new Percentage(pct.divide(ONE_HUNDRED, MathContext.DECIMAL64));
    }

    public static Percentage ofPercent(String pct) {
        return ofPercent(new BigDecimal(pct));
    }

    public static Percentage ofBps(BigDecimal bps) {
        return new Percentage(bps.divide(TEN_THOUSAND, MathContext.DECIMAL64));
    }

    public static Percentage ofBps(long bps) {
        return ofBps(BigDecimal.valueOf(bps));
    }

    public Money applyTo(Money base) {
        return base.multiply(this);
    }

    public BigDecimal asRatio() {
        return value;
    }

    public BigDecimal asPercent() {
        return value.multiply(ONE_HUNDRED, MathContext.DECIMAL64);
    }

    public BigDecimal asBps() {
        return value.multiply(TEN_THOUSAND, MathContext.DECIMAL64);
    }

    public Percentage plus(Percentage other) {
        return new Percentage(value.add(other.value));
    }

    public Percentage minus(Percentage other) {
        return new Percentage(value.subtract(other.value));
    }
}
