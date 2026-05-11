package ai.getfundflow.dsl.core.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PercentageTest {

    @Test
    void ofPercentStoresAsRatio() {
        assertThat(Percentage.ofPercent("1.5").asRatio()).isEqualByComparingTo("0.015");
    }

    @Test
    void ofBpsStoresAsRatio() {
        assertThat(Percentage.ofBps(25).asRatio()).isEqualByComparingTo("0.0025");
        assertThat(Percentage.ofBps(100).asRatio()).isEqualByComparingTo("0.01");
    }

    @Test
    void asPercentRoundtrips() {
        assertThat(Percentage.ofPercent("1.5").asPercent()).isEqualByComparingTo("1.5");
    }

    @Test
    void asBpsRoundtrips() {
        assertThat(Percentage.ofBps(150).asBps()).isEqualByComparingTo("150");
    }

    @Test
    void applyToMoneyMultiplies() {
        Money base = Money.of("1000000", "USD");
        Money fee = Percentage.ofPercent("2").applyTo(base);
        assertThat(fee.amount()).isEqualByComparingTo("20000.00");
    }

    @Test
    void plusAndMinus() {
        Percentage a = Percentage.ofPercent("1.5");
        Percentage b = Percentage.ofPercent("0.5");
        assertThat(a.plus(b).asPercent()).isEqualByComparingTo("2.0");
        assertThat(a.minus(b).asPercent()).isEqualByComparingTo("1.0");
    }

    @Test
    void valueEquality() {
        assertThat(new Percentage(new BigDecimal("0.015")))
                .isEqualTo(new Percentage(new BigDecimal("0.015")));
    }
}
