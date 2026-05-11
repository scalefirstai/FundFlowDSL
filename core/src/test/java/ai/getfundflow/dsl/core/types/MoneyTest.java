package ai.getfundflow.dsl.core.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void normalizesScaleToCurrencyDefaultFractionDigits() {
        Money usd = Money.of("100.123456", "USD");
        assertThat(usd.amount()).isEqualByComparingTo("100.12");
        assertThat(usd.amount().scale()).isEqualTo(2);
    }

    @Test
    void enforcesNoDecimalsForJpy() {
        Money jpy = Money.of("1000.49", "JPY");
        assertThat(jpy.amount()).isEqualByComparingTo("1000");
        assertThat(jpy.amount().scale()).isEqualTo(0);
    }

    @Test
    void halfEvenRoundingOnConstruction() {
        assertThat(Money.of("1.005", "USD").amount()).isEqualByComparingTo("1.00");
        assertThat(Money.of("1.015", "USD").amount()).isEqualByComparingTo("1.02");
    }

    @Test
    void plusSameCurrencyAdds() {
        Money a = Money.of("100.00", "USD");
        Money b = Money.of("250.50", "USD");
        assertThat(a.plus(b).amount()).isEqualByComparingTo("350.50");
    }

    @Test
    void plusDifferentCurrencyThrows() {
        Money usd = Money.of("100", "USD");
        Money eur = Money.of("100", "EUR");
        assertThatThrownBy(() -> usd.plus(eur))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void minusSubtracts() {
        assertThat(Money.of("100", "USD").minus(Money.of("30", "USD")).amount())
                .isEqualByComparingTo("70.00");
    }

    @Test
    void multiplyByScalar() {
        assertThat(Money.of("100.00", "USD").multiply(new BigDecimal("1.5")).amount())
                .isEqualByComparingTo("150.00");
    }

    @Test
    void multiplyByPercentage() {
        Money fee = Money.of("1000000", "USD").multiply(new Percentage(new BigDecimal("0.02")));
        assertThat(fee.amount()).isEqualByComparingTo("20000.00");
    }

    @Test
    void negateFlipsSign() {
        assertThat(Money.of("100", "USD").negate().amount()).isEqualByComparingTo("-100.00");
    }

    @Test
    void convertViaFxRate() {
        FxRate usdToEur = new FxRate(USD, EUR, new BigDecimal("0.92"), Instant.parse("2026-01-01T00:00:00Z"));
        Money result = Money.of("100", "USD").convert(usdToEur);
        assertThat(result.currency()).isEqualTo(EUR);
        assertThat(result.amount()).isEqualByComparingTo("92.00");
    }

    @Test
    void convertWithWrongSourceCurrencyThrows() {
        FxRate usdToEur = new FxRate(USD, EUR, new BigDecimal("0.92"), Instant.now());
        assertThatThrownBy(() -> Money.of("100", "JPY").convert(usdToEur))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void valueEqualityAcrossInstances() {
        assertThat(Money.of("100", "USD")).isEqualTo(Money.of("100.00", "USD"));
        assertThat(Money.of("100", "USD")).hasSameHashCodeAs(Money.of("100.00", "USD"));
    }

    @Test
    void zeroAndIsZero() {
        assertThat(Money.zero(JPY).isZero()).isTrue();
        assertThat(Money.of("0.001", "USD").isZero()).isTrue();
    }
}
