package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }

    /**
     * Construct money from an input amount. Rounds to the currency's default
     * fraction digits using HALF_EVEN — this is the I/O boundary where amounts
     * arrive from text literals or external data sources.
     */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(
                amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN),
                currency);
    }

    public static Money of(String amount, String currencyCode) {
        return of(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    /**
     * Construct money preserving the input amount's precision, without rounding
     * to currency fraction digits. Use this for per-unit prices, rates, and any
     * value that needs more precision than the currency's default scale —
     * e.g. a distribution-per-unit of {@code 0.116 USD} or a NAV-per-unit of
     * {@code 12.3456 USD}. Apply {@link #rounded()} at the output boundary when
     * formatting for settlement.
     */
    public static Money exact(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money exact(String amount, String currencyCode) {
        return exact(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return of(BigDecimal.ZERO, currency);
    }

    /**
     * Returns a Money with the amount rounded to the currency's default fraction
     * digits (HALF_EVEN). Use this at the output boundary when intermediate
     * arithmetic has produced a higher-precision value.
     */
    public Money rounded() {
        int scale = currency.getDefaultFractionDigits();
        if (amount.scale() == scale) return this;
        return new Money(amount.setScale(scale, RoundingMode.HALF_EVEN), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal scalar) {
        return new Money(amount.multiply(scalar, MathContext.DECIMAL64), currency);
    }

    public Money multiply(Percentage pct) {
        return new Money(amount.multiply(pct.asRatio(), MathContext.DECIMAL64), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public Money convert(FxRate rate) {
        return rate.apply(this);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
