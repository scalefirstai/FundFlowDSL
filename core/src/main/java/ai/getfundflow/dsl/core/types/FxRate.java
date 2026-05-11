package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public record FxRate(Currency from, Currency to, BigDecimal rate, Instant asOf) {

    public FxRate {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(asOf, "asOf");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("FX rate must be positive: " + rate);
        }
    }

    public Money apply(Money source) {
        if (!source.currency().equals(from)) {
            throw new CurrencyMismatchException(source.currency(), from);
        }
        BigDecimal converted = source.amount().multiply(rate, MathContext.DECIMAL64);
        return Money.of(converted, to);
    }

    public FxRate inverse(Instant asOf) {
        BigDecimal inv = BigDecimal.ONE.divide(rate, MathContext.DECIMAL64);
        return new FxRate(to, from, inv, asOf);
    }
}
