package ai.getfundflow.dsl.core.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DayCountTest {

    @Test
    void actual360OneYearIsAbove1() {
        BigDecimal yf = Actual360.INSTANCE.yearFraction(
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        assertThat(yf).isEqualByComparingTo(new BigDecimal("365").divide(new BigDecimal("360"), java.math.MathContext.DECIMAL64));
    }

    @Test
    void actual360ThirtyDays() {
        BigDecimal yf = Actual360.INSTANCE.yearFraction(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(yf).isEqualByComparingTo(new BigDecimal("30").divide(new BigDecimal("360"), java.math.MathContext.DECIMAL64));
    }

    @Test
    void actual365OneYearIsOne() {
        BigDecimal yf = Actual365.INSTANCE.yearFraction(
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        assertThat(yf).isEqualByComparingTo("1");
    }

    @Test
    void thirty360FullMonthIsThirty() {
        BigDecimal yf = Thirty360.INSTANCE.yearFraction(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));
        assertThat(yf).isEqualByComparingTo(new BigDecimal("30").divide(new BigDecimal("360"), java.math.MathContext.DECIMAL64));
    }

    @Test
    void thirty360HandlesEndOfMonth31Adjustment() {
        BigDecimal yf = Thirty360.INSTANCE.yearFraction(
                LocalDate.of(2026, 1, 30), LocalDate.of(2026, 3, 31));
        assertThat(yf).isEqualByComparingTo(new BigDecimal("60").divide(new BigDecimal("360"), java.math.MathContext.DECIMAL64));
    }

    @Test
    void thirty360FullYear() {
        BigDecimal yf = Thirty360.INSTANCE.yearFraction(
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        assertThat(yf).isEqualByComparingTo("1");
    }

    @Test
    void actualActualSameYearNonLeap() {
        BigDecimal yf = ActualActual.INSTANCE.yearFraction(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 1));
        assertThat(yf).isEqualByComparingTo(new BigDecimal("181").divide(new BigDecimal("365"), java.math.MathContext.DECIMAL64));
    }

    @Test
    void actualActualLeapYearOneYear() {
        BigDecimal yf = ActualActual.INSTANCE.yearFraction(
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        assertThat(yf).isEqualByComparingTo("1");
    }

    @Test
    void actualActualSameDateIsZero() {
        BigDecimal yf = ActualActual.INSTANCE.yearFraction(
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15));
        assertThat(yf).isEqualByComparingTo("0");
    }

    @Test
    void codesMatchSpecLiteralSyntax() {
        assertThat(Actual360.INSTANCE.code()).isEqualTo("actual/360");
        assertThat(Actual365.INSTANCE.code()).isEqualTo("actual/365");
        assertThat(Thirty360.INSTANCE.code()).isEqualTo("30/360");
        assertThat(ActualActual.INSTANCE.code()).isEqualTo("actual/actual");
    }
}
